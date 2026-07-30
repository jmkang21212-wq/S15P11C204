"""전사, 위험 감지 내역, 약속을 Spring으로 보낸다.

성격이 달라 다르게 다룬다.

  전사 : 양이 많고 지연이 상관없다  -> 모아서 배치 전송
  경고 : 적고 유실되면 안 된다      -> 즉시 개별 전송 + 재시도
  약속 : 경고와 같다                -> 즉시 개별 전송 + 재시도

전부 별도 스레드에서 돈다. Spring이 느리거나 죽어 있어도
실시간 감지와 클라이언트 경고는 영향을 받지 않아야 하기 때문이다.
"""

import logging
import queue
import threading
import time
from datetime import datetime, timezone
from typing import Optional

import httpx

from app.config import (
    SPRING_ALERT_PATH,
    SPRING_BASE_URL,
    SPRING_BATCH_SEC,
    SPRING_BATCH_SIZE,
    SPRING_MAX_RETRIES,
    SPRING_PROMISE_PATH,
    SPRING_QUEUE_MAX,
    SPRING_TIMEOUT,
    SPRING_TOKEN,
    SPRING_TRANSCRIPT_PATH,
)
from app.detect.dictionary import Hit
from app.detect.llm_judge import Verdict
from app.detect.promise import Promise
from app.stt.groq_stt import Transcript

log = logging.getLogger(__name__)

# 감지기 카테고리 -> Spring RISK_EVENTS.risk_category
#
# 두 쪽의 어휘가 다르다. 감지기는 판정에 필요한 만큼 세분화하고(threat, aggressive),
# Spring은 ck_risk_events_risk_category가 정한 6종만 받는다. 여기서 맞춰 보내지
# 않으면 Spring이 INSERT에서 제약 위반으로 실패한다.
#
# 대응하는 값이 없는 것은 OTHER로 보낸다. 위험 자체를 버리는 것보다 낫고,
# 원래 카테고리는 reason과 text에 남는다.
_RISK_CATEGORY = {
    "profanity": "PROFANITY",
    "insult": "DEFAMATION",
    "discrimination": "DISCRIMINATION",
    "sexual": "SEXUAL",
    "harassment": "SEXUAL",
    "threat": "OTHER",
    "aggressive": "OTHER",
}


def _risk_category(category: str) -> str:
    """Spring이 받는 카테고리로 바꾼다. 모르는 값은 OTHER."""
    return _RISK_CATEGORY.get((category or "").lower(), "OTHER")


def _iso(ts: float) -> str:
    """epoch 초 -> ISO8601 UTC. Java의 Instant.parse()가 그대로 받는다."""
    return (datetime.fromtimestamp(ts, timezone.utc)
            .isoformat(timespec="milliseconds")
            .replace("+00:00", "Z"))


class SpringSink:
    def __init__(self, base_url: str = SPRING_BASE_URL):
        self.base_url = base_url
        self.enabled = bool(base_url)

        self._transcripts: queue.Queue = queue.Queue(maxsize=SPRING_QUEUE_MAX)
        self._alerts: queue.Queue = queue.Queue(maxsize=SPRING_QUEUE_MAX)
        self._promises: queue.Queue = queue.Queue(maxsize=SPRING_QUEUE_MAX)
        self._stopping = threading.Event()
        self._client: Optional[httpx.Client] = None
        self._threads: list[threading.Thread] = []

        self.sent_transcripts = 0
        self.sent_alerts = 0
        self.sent_promises = 0
        self.dropped = 0
        self.failed = 0

    def start(self) -> "SpringSink":
        if not self.enabled:
            log.warning("SPRING_BASE_URL이 없어 Spring 전송을 끕니다. "
                        "감지 기능은 정상 동작하며 저장만 되지 않습니다.")
            return self

        headers = {"Content-Type": "application/json"}
        if SPRING_TOKEN:
            headers["Authorization"] = f"Bearer {SPRING_TOKEN}"
        self._client = httpx.Client(base_url=self.base_url, timeout=SPRING_TIMEOUT,
                                    headers=headers)

        self._threads = [
            threading.Thread(target=self._transcript_worker, daemon=True,
                             name="spring-transcript"),
            threading.Thread(target=self._alert_worker, daemon=True,
                             name="spring-alert"),
            threading.Thread(target=self._promise_worker, daemon=True,
                             name="spring-promise"),
        ]
        for t in self._threads:
            t.start()
        log.info("Spring 전송 활성화: %s", self.base_url)
        return self

    # --- 외부에서 부르는 것 ---

    def send_transcript(self, tr: Transcript) -> None:
        self._offer(self._transcripts, {
            "sessionId": tr.session_id,
            "seq": tr.seq,
            "text": tr.text,
            "startedAt": _iso(tr.start_ts),
            "endedAt": _iso(tr.end_ts),
            "forced": tr.forced,
        })

    def send_dictionary_alert(self, tr: Transcript, hits: list[Hit]) -> None:
        worst = max(hits, key=lambda h: h.rank)
        self._offer(self._alerts, {
            "sessionId": tr.session_id,
            "seq": tr.seq,
            "source": "DICTIONARY",
            "severity": worst.severity.upper(),
            "category": _risk_category(worst.category),
            "text": tr.text,
            "startedAt": _iso(tr.start_ts),
            "endedAt": _iso(tr.end_ts),
            "reason": None,
            "matches": [
                {"word": h.matched, "severity": h.severity.upper(),
                 "category": _risk_category(h.category),
                 "start": h.start, "end": h.end}
                for h in hits
            ],
        })

    def send_llm_alert(self, v: Verdict) -> None:
        self._offer(self._alerts, {
            "sessionId": v.session_id,
            "seq": v.seq,
            "source": "LLM",
            "severity": v.risk.upper(),
            "category": _risk_category(v.category),
            "text": v.text,
            "startedAt": _iso(v.start_ts),
            "endedAt": _iso(v.end_ts),
            "reason": v.reason,
            "matches": [],
        })

    def send_promise(self, p: Promise) -> None:
        """약속은 경고와 같은 큐를 쓴다. 건수가 적고 유실되면 안 되는 점이 같다."""
        self._offer(self._promises, {
            "sessionId": p.session_id,
            "seq": p.seq,
            "content": p.content,
            "rawText": p.raw_text,
            "deadlineText": p.deadline_text,
            # 한국어 기한 표현은 AI가 절대 시각으로 바꿔서 보낸다.
            # Spring이 "2주 안"을 파싱하게 두면 안 된다.
            "deadlineAt": _iso(p.deadline_at) if p.deadline_at else None,
            "condition": p.condition,
            "confidence": round(p.confidence, 3),
            "spokenAt": _iso(p.spoken_at),
        })

    def stop(self, timeout: float = 5.0) -> None:
        self._stopping.set()
        for t in self._threads:
            t.join(timeout=timeout)
        if self._client is not None:
            self._client.close()

    # --- 내부 ---

    def _offer(self, q: queue.Queue, payload: dict) -> None:
        if not self.enabled:
            return
        try:
            q.put_nowait(payload)
        except queue.Full:
            # Spring이 오래 죽어 있는 상황. 최신 것을 살린다.
            try:
                q.get_nowait()
                self.dropped += 1
                q.put_nowait(payload)
            except (queue.Empty, queue.Full):
                self.dropped += 1

    def _transcript_worker(self) -> None:
        """일정 개수가 모이거나 일정 시간이 지나면 묶어서 보낸다."""
        batch: list[dict] = []
        last_flush = time.monotonic()

        while not self._stopping.is_set():
            try:
                batch.append(self._transcripts.get(timeout=0.2))
            except queue.Empty:
                pass

            due = (len(batch) >= SPRING_BATCH_SIZE
                   or (batch and time.monotonic() - last_flush >= SPRING_BATCH_SEC))
            if due:
                self._flush_transcripts(batch)
                batch = []
                last_flush = time.monotonic()

        # 종료 직전 남은 것까지 마저 보낸다. 리포트에 구멍이 생기면 안 된다.
        while True:
            try:
                batch.append(self._transcripts.get_nowait())
            except queue.Empty:
                break
        if batch:
            self._flush_transcripts(batch)

    def _flush_transcripts(self, batch: list[dict]) -> None:
        if self._post(SPRING_TRANSCRIPT_PATH, {"items": batch}):
            self.sent_transcripts += len(batch)

    def _alert_worker(self) -> None:
        while not self._stopping.is_set():
            try:
                payload = self._alerts.get(timeout=0.2)
            except queue.Empty:
                continue
            if self._post(SPRING_ALERT_PATH, payload):
                self.sent_alerts += 1

    def _promise_worker(self) -> None:
        while not self._stopping.is_set():
            try:
                payload = self._promises.get(timeout=0.2)
            except queue.Empty:
                continue
            if self._post(SPRING_PROMISE_PATH, payload):
                self.sent_promises += 1

    def _post(self, path: str, payload: dict) -> bool:
        for attempt in range(SPRING_MAX_RETRIES + 1):
            try:
                r = self._client.post(path, json=payload)
                if r.status_code < 300:
                    return True
                # 4xx는 우리 쪽 문제라 재시도해도 같은 결과다.
                if 400 <= r.status_code < 500:
                    log.error("Spring 거부 %s %s: %s",
                              r.status_code, path, r.text[:200])
                    self.failed += 1
                    return False
                log.warning("Spring %s 응답 %s (%d/%d)",
                            path, r.status_code, attempt + 1, SPRING_MAX_RETRIES)
            except httpx.HTTPError as e:
                log.warning("Spring 전송 실패 %s: %s (%d/%d)",
                            path, e, attempt + 1, SPRING_MAX_RETRIES)
            if attempt < SPRING_MAX_RETRIES:
                time.sleep(0.5 * (2 ** attempt))  # 0.5 -> 1 -> 2초

        log.error("Spring 전송 포기 %s", path)
        self.failed += 1
        return False

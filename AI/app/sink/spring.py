"""전사, 위험 감지 내역, 약속을 Spring으로 보낸다.

성격이 달라 다르게 다룬다.

  전사 : 양이 많고 지연이 상관없다  -> 모아서 배치 전송
  경고 : 적고 유실되면 안 된다      -> 즉시 개별 전송 + 재시도
  약속 : 저장된 id를 되받아야 한다  -> 부르는 자리에서 동기 전송 + 재시도

전사와 경고는 별도 스레드에서 돈다. Spring이 느리거나 죽어 있어도
실시간 감지와 클라이언트 경고는 영향을 받지 않아야 하기 때문이다.

약속만 예외다. 프론트가 확정/무시 버튼을 누르려면 PROMISES의 행 id가 필요한데
그 id는 POST 응답에만 있다. 큐에 던지고 잊으면 알 방법이 없어서, 약속은 부르는
쪽이 응답을 기다린다. 건수가 분당 몇 건 수준이라 감당된다.
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


def _row_id(body: dict) -> Optional[int]:
    """저장 응답에서 행 id를 꺼낸다: {"success": true, "data": {"id": 4821}}.

    id가 없어도 저장 자체는 성공한 것이라 실패로 만들지 않는다. 프론트는 id 없는
    약속 카드를 받아 확정/무시 버튼만 잠근다.
    """
    data = body.get("data")
    if not isinstance(data, dict):
        return None
    try:
        return int(data["id"])
    except (KeyError, TypeError, ValueError):
        log.warning("저장 응답에 id가 없습니다: %.200s", data)
        return None


class SpringSink:
    def __init__(self, base_url: str = SPRING_BASE_URL):
        self.base_url = base_url
        self.enabled = bool(base_url)

        # WS 세션 id(문자열) -> broadcast_sessions.id(숫자)
        #
        # 프론트가 만드는 세션 id는 탭 충돌을 피하려는 임의 문자열이라 Spring의
        # 방송 세션과 아무 관계가 없다. 저장하려면 숫자 id가 필요한데 AI는 그 값을
        # 알 방법이 없어서, 핸드셰이크에서 받아 여기에 묶어둔다.
        self._session_ids: dict[str, int] = {}
        self._session_lock = threading.Lock()

        # 약속은 큐를 쓰지 않는다. 모듈 docstring 참고.
        self._transcripts: queue.Queue = queue.Queue(maxsize=SPRING_QUEUE_MAX)
        self._alerts: queue.Queue = queue.Queue(maxsize=SPRING_QUEUE_MAX)
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
        ]
        for t in self._threads:
            t.start()
        log.info("Spring 전송 활성화: %s", self.base_url)
        return self

    # --- 외부에서 부르는 것 ---

    def bind_session(self, session_id: str, broadcast_session_id: int) -> None:
        """핸드셰이크에서 받은 Spring 방송 세션 번호를 묶는다."""
        with self._session_lock:
            self._session_ids[session_id] = broadcast_session_id

    def unbind_session(self, session_id: str) -> None:
        with self._session_lock:
            self._session_ids.pop(session_id, None)

    def release_session_later(self, session_id: str) -> None:
        """세션이 끊긴 뒤 잠시 두었다가 정리한다.

        끊는 즉시 풀면 아직 큐에 남아 있는 전사 배치가 보낼 URL을 잃는다.
        배치 주기와 재시도 백오프를 합친 것보다 넉넉히 기다린다.
        """
        delay = SPRING_BATCH_SEC + SPRING_TIMEOUT * (SPRING_MAX_RETRIES + 1)
        timer = threading.Timer(delay, self.unbind_session, args=(session_id,))
        timer.daemon = True
        timer.start()

    def _broadcast_session_id(self, session_id: str) -> Optional[int]:
        with self._session_lock:
            return self._session_ids.get(session_id)

    def send_transcript(self, tr: Transcript) -> None:
        self._offer(self._transcripts, (tr.session_id, {
            "text": tr.text,
            "startedAt": _iso(tr.start_ts),
            "endedAt": _iso(tr.end_ts),
        }))

    def send_dictionary_alert(self, tr: Transcript, hits: list[Hit]) -> None:
        """경고에 발화 원문을 함께 보낸다.

        경고는 즉시 전송이고 전사는 배치라 경고가 자기 전사보다 먼저 도착한다.
        Spring은 이 text로 전사를 Upsert한 뒤 위험 발언을 붙이므로, 두 스트림의
        도착 순서를 맞출 필요가 없다.
        """
        worst = max(hits, key=lambda h: h.rank)
        self._offer(self._alerts, (tr.session_id, {
            "source": "DICTIONARY",
            "severity": worst.severity.upper(),
            "category": _risk_category(worst.category),
            "text": tr.text,
            "startedAt": _iso(tr.start_ts),
            "endedAt": _iso(tr.end_ts),
            "reason": None,
        }))

    def send_llm_alert(self, v: Verdict) -> None:
        self._offer(self._alerts, (v.session_id, {
            "source": "LLM",
            "severity": v.risk.upper(),
            "category": _risk_category(v.category),
            "text": v.text,
            "startedAt": _iso(v.start_ts),
            "endedAt": _iso(v.end_ts),
            "reason": v.reason,
        }))

    def send_promise(self, p: Promise) -> Optional[int]:
        """약속을 저장하고 PROMISES의 행 id를 돌려준다. 응답을 기다린다.

        프론트는 이 id로 확정/무시를 호출한다. id 없이 카드만 띄우면 버튼이 가리킬
        대상이 없어서, 다른 전송과 달리 큐에 넣지 않고 여기서 끝낸다.

        Spring이 죽어 있으면 재시도 백오프만큼(0.5+1+2초 + 타임아웃) 붙잡힌다.
        이 함수는 약속 디스패치 스레드에서만 불리므로 감지와 오디오는 멀쩡하고
        약속 카드만 그만큼 늦게 뜬다. 저장하지 못했으면 None이다.
        """
        if not self.enabled:
            return None
        path = self._path(SPRING_PROMISE_PATH, p.session_id)
        if path is None:
            return None

        body = self._post(path, {
            "content": p.content,
            "rawText": p.raw_text,
            # 한국어 기한 표현은 AI가 절대 시각으로 바꿔서 보낸다.
            # Spring이 "2주 안"을 파싱하게 두면 안 된다.
            "deadlineAt": _iso(p.deadline_at) if p.deadline_at else None,
            "spokenAt": _iso(p.spoken_at),
        })
        if body is None:
            return None

        self.sent_promises += 1
        return _row_id(body)

    def stop(self, timeout: float = 5.0) -> None:
        self._stopping.set()
        for t in self._threads:
            t.join(timeout=timeout)
        if self._client is not None:
            self._client.close()

    # --- 내부 ---

    def _offer(self, q: queue.Queue, payload: tuple) -> None:
        """payload는 (WS 세션 id, 본문) 쌍이다. 세션 id는 보낼 URL을 정하는 데 쓴다."""
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
        batch: list[tuple] = []
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

    def _flush_transcripts(self, batch: list[tuple]) -> None:
        """세션마다 URL이 다르므로 묶어서 세션별로 보낸다."""
        by_session: dict[str, list[dict]] = {}
        for session_id, item in batch:
            by_session.setdefault(session_id, []).append(item)

        for session_id, items in by_session.items():
            path = self._path(SPRING_TRANSCRIPT_PATH, session_id)
            if path and self._post(path, {"items": items}) is not None:
                self.sent_transcripts += len(items)

    def _alert_worker(self) -> None:
        while not self._stopping.is_set():
            try:
                session_id, payload = self._alerts.get(timeout=0.2)
            except queue.Empty:
                continue
            path = self._path(SPRING_ALERT_PATH, session_id)
            if path and self._post(path, payload) is not None:
                self.sent_alerts += 1

    def _path(self, template: str, session_id: str) -> Optional[str]:
        """방송 세션 번호가 묶여 있지 않으면 보낼 곳이 없다.

        프론트가 핸드셰이크에 broadcastSessionId를 넣지 않은 경우다. 감지와 프론트
        전송은 그대로 돌고 Spring 저장만 건너뛴다.
        """
        broadcast_session_id = self._broadcast_session_id(session_id)
        if broadcast_session_id is None:
            self.dropped += 1
            log.warning("방송 세션 번호가 없어 Spring 전송을 건너뜁니다: session_id=%s",
                        session_id)
            return None
        return template.format(session_id=broadcast_session_id)

    def _post(self, path: str, payload: dict) -> Optional[dict]:
        """성공하면 응답 본문, 실패하면 None.

        본문이 JSON 객체가 아니면 빈 dict다. 빈 응답과 실패를 구분해야 해서
        bool이 아니므로, 호출부는 `is not None`으로 판정한다.
        """
        for attempt in range(SPRING_MAX_RETRIES + 1):
            try:
                r = self._client.post(path, json=payload)
                if r.status_code < 300:
                    try:
                        body = r.json()
                    except ValueError:
                        return {}
                    return body if isinstance(body, dict) else {}
                # 4xx는 우리 쪽 문제라 재시도해도 같은 결과다.
                if 400 <= r.status_code < 500:
                    log.error("Spring 거부 %s %s: %s",
                              r.status_code, path, r.text[:200])
                    self.failed += 1
                    return None
                log.warning("Spring %s 응답 %s (%d/%d)",
                            path, r.status_code, attempt + 1, SPRING_MAX_RETRIES)
            except httpx.HTTPError as e:
                log.warning("Spring 전송 실패 %s: %s (%d/%d)",
                            path, e, attempt + 1, SPRING_MAX_RETRIES)
            if attempt < SPRING_MAX_RETRIES:
                time.sleep(0.5 * (2 ** attempt))  # 0.5 -> 1 -> 2초

        log.error("Spring 전송 포기 %s", path)
        self.failed += 1
        return None

import json
import logging
import queue
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Iterator, Optional

from openai import OpenAI, RateLimitError

from app.config import (
    GMS_BASE_URL,
    GMS_KEY,
    LLM_CONTEXT_SEC,
    LLM_MAX_RETRIES,
    LLM_MIN_TEXT_LEN,
    LLM_MODEL,
    LLM_QUEUE_MAX,
    LLM_REASONING_EFFORT,
    LLM_WORKERS,
)
from app.stt.groq_stt import Transcript

log = logging.getLogger(__name__)

_SENTINEL = object()
_RISK_LEVELS = ("none", "low", "medium", "high")

DEVELOPER_PROMPT = """너는 실시간 대화 모니터링 시스템의 위험 발언 판정기다.
'판정 대상' 발화가 위험 발언인지 판단하라. 문맥은 참고용이고, 판정하는 것은 대상 발화다.

위험 유형:
- profanity: 비속어, 욕설
- insult: 특정인을 향한 모욕
- threat: 위협, 협박
- harassment: 성희롱, 괴롭힘
- discrimination: 차별 발언

주의:
- 음성인식 결과라 오타가 섞일 수 있다. 발음이 비슷한 욕설을 감안하라.
- 단순한 불평이나 부정적 감정 표현은 위험이 아니다.
- 농담이나 인용이어도 표현 자체가 공격적이면 위험으로 본다.

반드시 아래 형식의 JSON만 출력하라:
{"risk": "none|low|medium|high", "category": "위 유형 중 하나 또는 none", "reason": "한 문장"}"""


@dataclass
class Verdict:
    seq: int
    text: str
    start_ts: float
    end_ts: float
    risk: str
    category: str
    reason: str
    latency: float
    session_id: str = ""

    @property
    def is_risky(self) -> bool:
        return self.risk in ("low", "medium", "high")


class RollingContext:
    def __init__(self, window_sec: float = LLM_CONTEXT_SEC):
        self.window_sec = window_sec
        self._items: deque[tuple[float, str]] = deque()
        self._lock = threading.Lock()

    def add(self, tr: Transcript) -> None:
        with self._lock:
            self._items.append((tr.start_ts, tr.text))
            cutoff = tr.end_ts - self.window_sec
            while self._items and self._items[0][0] < cutoff:
                self._items.popleft()

    def snapshot(self, before_ts: float) -> str:
        """판정 대상보다 앞선 발화들만 모아 문맥 문자열로 만든다."""
        with self._lock:
            lines = [text for ts, text in self._items
                     if ts < before_ts and ts >= before_ts - self.window_sec]
        return "\n".join(lines)


class LLMJudge:
    def __init__(self, workers: int = LLM_WORKERS, maxsize: int = LLM_QUEUE_MAX):
        if not GMS_KEY:
            raise RuntimeError(".env에 GMS_KEY가 없습니다.")
        self._client = OpenAI(api_key=GMS_KEY, base_url=GMS_BASE_URL)
        self._in: queue.Queue = queue.Queue(maxsize=maxsize)
        self._out: queue.Queue = queue.Queue()
        self._threads = [
            threading.Thread(target=self._worker, name=f"llm-{i}", daemon=True)
            for i in range(workers)
        ]
        self._stopping = threading.Event()

        self.submitted = 0
        self.completed = 0
        self.dropped = 0
        self.failed = 0
        self.skipped = 0

    def start(self) -> "LLMJudge":
        for t in self._threads:
            t.start()
        return self

    def submit(self, tr: Transcript, context: str) -> bool:
        if len(tr.text.strip()) < LLM_MIN_TEXT_LEN:
            self.skipped += 1
            return False

        self.submitted += 1
        job = (tr, context)
        try:
            self._in.put_nowait(job)
            return True
        except queue.Full:
            try:
                self._in.get_nowait()  # 오래된 판정은 이미 늦었다
                self.dropped += 1
                self._in.put_nowait(job)
                return True
            except (queue.Empty, queue.Full):
                self.dropped += 1
                return False

    def results(self) -> Iterator[Verdict]:
        while True:
            item = self._out.get()
            if item is _SENTINEL:
                return
            yield item

    def stop(self, timeout: float = 5.0) -> None:
        self._stopping.set()
        for t in self._threads:
            t.join(timeout=timeout)
        self._out.put(_SENTINEL)

    # --- 내부 ---

    def _worker(self) -> None:
        while not self._stopping.is_set():
            try:
                tr, context = self._in.get(timeout=0.2)
            except queue.Empty:
                continue
            try:
                verdict = self._judge(tr, context)
            except Exception:
                self.failed += 1
                log.exception("판정 실패 seq=%s", tr.seq)
                continue
            if verdict is not None:
                self.completed += 1
                self._out.put(verdict)

    def _judge(self, tr: Transcript, context: str) -> Optional[Verdict]:
        messages = [
            {"role": "developer", "content": DEVELOPER_PROMPT},
            {"role": "user",
             "content": f"[직전 문맥]\n{context or '(없음)'}\n\n[판정 대상]\n{tr.text}"},
        ]

        started = time.perf_counter()
        raw = self._call_with_retry(messages, tr.seq)
        latency = time.perf_counter() - started
        if raw is None:
            return None

        risk, category, reason = _parse(raw, tr.seq)
        return Verdict(seq=tr.seq, text=tr.text, start_ts=tr.start_ts,
                       end_ts=tr.end_ts, risk=risk, category=category,
                       reason=reason, latency=latency, session_id=tr.session_id)

    def _call_with_retry(self, messages: list, seq: int) -> Optional[str]:
        for attempt in range(LLM_MAX_RETRIES + 1):
            try:
                r = self._client.chat.completions.create(
                    model=LLM_MODEL,
                    messages=messages,
                    response_format={"type": "json_object"},
                    reasoning_effort=LLM_REASONING_EFFORT,
                )
                return r.choices[0].message.content
            except RateLimitError:
                if attempt == LLM_MAX_RETRIES:
                    break
                time.sleep(1.0 * (attempt + 1))
            except Exception as e:
                if attempt == LLM_MAX_RETRIES:
                    log.error("판정 포기 seq=%s: %s", seq, e)
                    break
                time.sleep(0.5)
        self.failed += 1
        return None


def _parse(raw: str, seq: int) -> tuple[str, str, str]:
    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        log.warning("판정 JSON 파싱 실패 seq=%s raw=%r", seq, raw)
        return "none", "none", "(응답 파싱 실패)"

    risk = str(data.get("risk", "none")).lower()
    if risk not in _RISK_LEVELS:
        log.warning("알 수 없는 risk 값 seq=%s risk=%r", seq, risk)
        risk = "none"
    return risk, str(data.get("category", "none")), str(data.get("reason", ""))

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
    LLM_ALERT_MIN_RISK,
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
_ALERT_MIN_INDEX = _RISK_LEVELS.index(LLM_ALERT_MIN_RISK)

DEVELOPER_PROMPT = """너는 실시간 대화 모니터링 시스템의 위험 발언 판정기다.
'판정 대상' 발화가 위험 발언인지 판단하라. 문맥은 참고용이고, 판정하는 것은 대상 발화다.

판정 기준은 '심한 욕설·모욕·위협이 실제로 들어 있는가'다.
표현이 거칠지 않은 평범한 대화는 내용이 부정적이어도 위험이 아니다.

위험 유형:
- profanity: 비속어, 욕설
- insult: 특정인을 향한 모욕
- threat: 위협, 협박
- sexual: 성적인 표현, 이른바 '성드립'
- harassment: 특정인을 향한 성희롱, 집요한 괴롭힘
- discrimination: 차별 발언

등급은 '위험한지'가 아니라 '얼마나 심한지'를 뜻한다.
위험이 아니라고 판단하면 반드시 none을 골라라. low는 "위험이 약하다"가 아니라
"가벼운 비속어가 실제로 있다"는 뜻이다.

- none: 욕설·모욕·위협·성적 표현이 없다. 대부분의 발화가 여기 해당한다.
        판단이 애매하거나 확신이 없을 때도 none을 골라라.
- low: 가벼운 비속어나 거친 말이 있다. (닥쳐, 꺼져, 재수없어)
- medium: 분명한 욕설이나 모욕이 있다. (지랄, 등신, 대가리)
- high: 심한 욕설, 직접적인 위협, 노골적인 성적 표현이다. (씨발, 개새끼, 죽여버린다)

none이 아니면 전부 스트리머에게 알림이 간다. 애매한 것을 올려 잡지 마라.

주의:
- 문맥은 대상 발화의 뜻을 이해하는 데만 쓴다. 직전 발화에 욕설이 있었다는 이유로
  대상 발화의 등급을 올리지 마라. 앞에서 욕을 했더라도 대상 발화 자체에 위험 표현이
  없으면 none이다.
  예: 문맥이 "닥치라고 했잖아"여도 대상 발화 "이게 보여 올려서"는 none이다.
- 음성인식 결과라 오타가 섞일 수 있다. 발음이 비슷한 욕설을 감안하라.
- 단순한 불평이나 부정적 감정 표현은 위험이 아니다.
- 욕설 없이 상대를 비판하거나 지적하는 말은 모욕이 아니다.
  예: "너가 말하는 건 잘못 가져오고", "그건 네가 틀렸어", "왜 그렇게 했어"
- 발화가 중간에 잘려 뜻이 불완전하면 none으로 판정하라.
  예: "상대방을 피해", "그래서 내가 말한"
- 욕설을 하는 것이 아니라 '욕설에 대해 말하는' 발화는 위험이 아니다.
  예: "님들 욕 하지 마세요", "욕설 감지 잘 되네", "채팅에 욕 쓴 사람 누구야"
- 남의 발언을 옮기거나 신고하는 인용은 위험이 아니다.
  예: "누가 저보고 병신이래요", "아까 개새끼라고 한 사람 나가주세요"
- 감탄이나 강조로 쓰인 표현은 위험이 아니다.
  예: "미친 이걸 살리네", "개잘하네"
- 다만 인용이나 농담의 형식을 빌려 특정인을 공격하면 위험이다.
  예: "쟤 병신이라던데", "농담인데 너 좀 꺼져줄래"

성적 표현(sexual) 판정:
- 방송 플랫폼 제재로 이어지므로 놓치지 않아야 한다.
- 노골적인 성행위·성기 언급은 high다.
- 성적 함의를 노린 농담, 이른바 성드립은 단어가 노골적이지 않아도 medium이다.
  예: "그거 되게 크네요", "밤에 혼자 뭐 하세요?", "누나 오늘 옷이 왜 그래요"
- 반대로 성적 의도가 없는 신체·의학·일상 표현은 none이다.
  예: "가슴이 두근거린다", "엉덩이가 아파", "산부인과 다녀왔어"
- 판단이 갈리면 none을 골라라. 평범한 대화에 성적 경고가 붙는 쪽이 더 나쁘다.

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
        """알림으로 내보낼 판정인지.

        category가 none이면 risk 값과 무관하게 위험이 아니다. LLM이 "위험 표현은
        없지만 위험도는 낮음"이라는 뜻으로 risk=low, category=none을 함께 주는 일이
        있는데, 그때 알림이 나가면 평범한 대화에 경고가 붙는다.
        """
        if not self.category or self.category == "none":
            return False
        try:
            return _RISK_LEVELS.index(self.risk) >= _ALERT_MIN_INDEX
        except ValueError:
            return False


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

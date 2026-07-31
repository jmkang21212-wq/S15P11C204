"""발화에서 스트리머가 시청자에게 한 약속을 추출한다.

위험 감지와 구조가 반대다. 위험 발언은 사전매칭이 주력이고 LLM이 회색지대를
맡았지만, 약속은 "2주 안으로 공포게임 할게요"처럼 의미를 이해해야 해서
LLM이 주력이다.

그렇다고 모든 발화를 LLM에 보낼 수는 없다(레이트리밋·비용). 다행히 한국어
약속은 어미로 드러나므로, 값싼 정규식으로 후보를 먼저 거른다.

  전사 -> [어미 게이트] -> 후보만 LLM -> 약속 추출 -> Spring
"""

import json
import logging
import queue
import re
import threading
import time
from dataclasses import dataclass
from typing import Iterator, Optional

from openai import OpenAI, RateLimitError

from app.config import (
    GMS_BASE_URL,
    GMS_KEY,
    LLM_MAX_RETRIES,
    LLM_MODEL,
    LLM_REASONING_EFFORT,
    PROMISE_DEDUP_SEC,
    PROMISE_MIN_CONFIDENCE,
    PROMISE_QUEUE_MAX,
    PROMISE_WORKERS,
)
from app.detect.normalize import normalize
from app.stt.groq_stt import Transcript

log = logging.getLogger(__name__)

_SENTINEL = object()

# 종성이 'ㄹ'인 한글 음절 399개 (할, 볼, 갈, 켤, 드릴 ...).
#
# 주의: '할'은 ㅎ+ㅏ+ㄹ이 하나로 합쳐진 단일 문자다. 정규식에 'ㄹ게'라고 써도
# 절대 안 걸린다. '-ㄹ게요' 어미를 잡으려면 종성이 ㄹ인 음절을 직접 나열해야 한다.
_RIEUL_FINAL = "".join(
    chr(0xAC00 + cho * 588 + vowel * 28 + 8)   # 종성 인덱스 8 = ㄹ
    for cho in range(19) for vowel in range(21)
)

# 약속 어미 게이트.
# 놓치는 것(미탐)이 헛된 LLM 호출보다 나쁘므로 넉넉하게 잡는다.
# 여기 걸린다고 약속인 것은 아니고, 최종 판단은 LLM이 한다.
_PROMISE_HINTS = re.compile(
    "("
    rf"[{_RIEUL_FINAL}]\s*게"                          # 할게요, 켤게, 보여드릴게
    rf"|[{_RIEUL_FINAL}]\s*거\s*(예요|에요|야|임|다)"   # 할 거예요
    rf"|[{_RIEUL_FINAL}]\s*(예정|계획)"                 # 할 예정
    r"|겠(습니다|어요|음|네|다)"                        # 하겠습니다
    r"|기로\s*(했|하|함)"                               # 하기로 했어요
    r"|약속|공약"
    ")"
)

# 기한 표현 게이트.
#
# 이 프로젝트는 '언제까지'가 붙은 약속만 추적한다. 기한 없는 다짐("다음에 꼭
# 할게요")과 순수 조건부 약속("구독자 1000명 되면")은 캘린더에 올릴 수 없어 뺀다.
#
# 어미만 보면 '-ㄹ 거야'가 한국어에서는 그냥 미래형이라("비가 올 거예요",
# "종이 찢어버릴 거야") 약속이 아닌 발화가 대량으로 새어 나온다. 기한 표현을
# 함께 요구하면 그 부류가 LLM 호출 전에 걸러진다.
#
# 여기도 게이트라 넉넉하게 잡는다. 진짜 기준은 LLM이 계산한 deadline_days다.
_DATE_HINTS = re.compile(
    "("
    r"오늘|내일|낼모레|낼|모레|글피"
    r"|이번\s*(주|달|주말)|다음\s*(주|달|해)|담\s*주|내달|내년|올해"
    r"|주말|평일|연말|월말|말일"
    r"|[월화수목금토일]\s*요일|[월화수목금토일]욜"
    r"|\d+\s*월\s*\d+\s*일|\d+\s*[/.]\s*\d+"
    r"|하루|이틀|사흘|나흘|닷새|엿새|이레|여드레|아흐레|열흘|보름"
    r"|일주일|이주일|삼주일|며칠|반나절|반시간|자정|정오"
    # "방송 끝나고 편집본 올릴게요" — 날짜가 안 붙었지만 방송이 끝나는 날,
    # 즉 오늘로 확정된다. 스트리머가 자주 쓰는 표현이라 넣는다.
    r"|방송\s*(끝|마치|종료|끄)"
    # 수사 + 기간 단위: 3일, 두 달, 몇 주, 24시간, 5분
    #
    # '분'을 빼면 "5분 뒤에 할게요"가 통째로 안 걸린다. 같은 자리의 '시간'은
    # 걸리는데 '분'만 빠져 있으면 사용자 눈에는 그냥 고장으로 보인다.
    r"|(\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|몇)\s*(일|주일|주|달|개월|년|시간|분)"
    r"|\d+\s*시"
    ")"
)


@dataclass
class Promise:
    seq: int
    session_id: str
    content: str              # 약속 내용 (간결한 명사구)
    raw_text: str             # 발화 원문
    deadline_text: Optional[str]   # 기한 표현 원문 ("2주 안")
    deadline_at: Optional[float]   # 해석된 절대 시각 (epoch). 모르면 None
    condition: Optional[str]       # 조건부 약속의 조건
    confidence: float
    spoken_at: float


def looks_like_promise(text: str, context: str = "") -> bool:
    """LLM에 보낼 가치가 있는지 값싸게 판단한다.

    약속 어미와 기한 표현이 **둘 다** 있어야 통과한다.

    기한은 문맥에서 찾아도 인정한다. 발화가 6초에서 잘리면 "다음 주에"와
    "공포게임 할게요"가 서로 다른 조각이 되는데, 발화만 보면 멀쩡한 약속을
    놓치기 때문이다. 문맥까지 보는 만큼 통과분이 늘지만 최종 판단은 LLM이 하고
    _build()가 기한 없는 결과를 다시 버린다.
    """
    if not _PROMISE_HINTS.search(text):
        return False
    return bool(_DATE_HINTS.search(text) or _DATE_HINTS.search(context))


DEVELOPER_PROMPT = """너는 1인 방송 스트리머의 발화에서 '시청자에게 한 약속'을 추출한다.

약속이란 스트리머가 시청자에게 앞으로 하겠다고 밝힌 구체적 행동이다.
문맥은 참고용이고, '판정 대상' 발화에서 나온 약속만 추출한다.

**언제 할 것인지 날짜를 계산할 수 있는 약속만 추출한다.**
기한을 알 수 없으면 약속이라도 추출하지 않는다.

추출한다:
- "2주 안으로 공포게임 할게요"        -> deadline_days 14
- "내일 저녁 8시에 방송 켤게요"        -> deadline_days 1
- "이번 주말에 신작 리뷰 할 거예요"    -> 발화일 기준 토요일까지 며칠인지
- "30분 뒤에 신곡 공개할게요"          -> deadline_days 0 (오늘 안에 하는 것)
- "방송 끝나고 편집본 올릴게요"        -> deadline_days 0 (오늘 방송이 끝나는 날)

deadline_days는 '며칠 뒤'를 나타내는 정수다. 몇 분 뒤, 몇 시간 뒤, 방송 끝나고처럼
오늘 안에 끝나는 기한은 0으로 적는다. null이 아니다.

추출하지 않는다:
- 기한 없는 다짐: "다음에 꼭 스피드런 할게요", "언젠가 해볼게요"
- 날짜를 못 정하는 조건부: "구독자 1000명 되면 얼굴 공개할게요"
- 지금 당장 하는 행동: "이거 한번 해볼게요", "물 좀 마실게요"
- 막연한 희망이나 감상: "언젠가 해보고 싶네요"
- 시청자 요청을 되읽기만 한 것

특히 '-ㄹ 거야 / -ㄹ 거예요'는 한국어에서 단순한 미래 표현일 뿐 약속이 아니다.
"종이 찢어버릴 거야", "비 올 거예요", "밥 먹을 거야"는 전부 약속이 아니다.
시청자에게 하겠다고 밝힌 것이 아니면 제외한다.

조건과 기한이 함께 있으면("다음 주에 100만 가면 공약 지킬게요") 추출하고
condition에 조건을 적는다. 조건만 있고 날짜가 없으면 추출하지 않는다.

주의:
- 음성인식 결과라 오타가 있을 수 있다.
- 발화가 6초에서 잘렸을 수 있으니 문맥에 기한이 있으면 함께 본다.

각 약속에 대해:
- content: 약속 내용을 간결한 명사구로 (예: "공포게임 방송")
- deadline_text: 기한 표현을 원문 그대로 ("2주 안으로")
- deadline_days: 발화일 기준 며칠 뒤인지 정수. **계산 불가면 그 약속은 추출하지 않는다**
- condition: 조건이 함께 있으면 그 조건, 아니면 null
- confidence: 0.0~1.0

약속이 없으면 빈 배열을 반환한다.

반드시 아래 JSON만 출력하라:
{"promises": [{"content": "...", "deadline_text": null, "deadline_days": null, "condition": null, "confidence": 0.9}]}"""


class PromiseExtractor:
    def __init__(self, workers: int = PROMISE_WORKERS,
                 maxsize: int = PROMISE_QUEUE_MAX):
        if not GMS_KEY:
            raise RuntimeError(".env에 GMS_KEY가 없습니다.")
        self._client = OpenAI(api_key=GMS_KEY, base_url=GMS_BASE_URL)
        self._in: queue.Queue = queue.Queue(maxsize=maxsize)
        self._out: queue.Queue = queue.Queue()
        self._threads = [
            threading.Thread(target=self._worker, name=f"promise-{i}", daemon=True)
            for i in range(workers)
        ]
        self._stopping = threading.Event()

        # 같은 약속을 반복해서 말하거나, 6초 강제분할로 두 번 잡히는 것을 막는다.
        self._seen: dict[str, dict[str, float]] = {}
        self._seen_lock = threading.Lock()

        self.gated = 0        # 게이트에서 걸러진 수 (LLM 호출을 아낀 횟수)
        self.submitted = 0
        self.extracted = 0
        self.no_deadline = 0  # 기한이 없어 버린 수
        self.deduped = 0
        self.dropped = 0
        self.failed = 0

    def start(self) -> "PromiseExtractor":
        for t in self._threads:
            t.start()
        return self

    def submit(self, tr: Transcript, context: str) -> bool:
        """어미와 기한 게이트를 통과한 발화만 추출 대기열에 넣는다."""
        if not looks_like_promise(tr.text, context):
            self.gated += 1
            return False

        self.submitted += 1
        job = (tr, context)
        try:
            self._in.put_nowait(job)
            return True
        except queue.Full:
            self.dropped += 1
            return False

    def results(self) -> Iterator[Promise]:
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

    def forget(self, session_id: str) -> None:
        """세션이 끝나면 중복 판정 기록을 지운다."""
        with self._seen_lock:
            self._seen.pop(session_id, None)

    # --- 내부 ---

    def _worker(self) -> None:
        while not self._stopping.is_set():
            try:
                tr, context = self._in.get(timeout=0.2)
            except queue.Empty:
                continue
            try:
                for promise in self._extract(tr, context):
                    self.extracted += 1
                    self._out.put(promise)
            except Exception:
                self.failed += 1
                log.exception("약속 추출 실패 seq=%s", tr.seq)

    def _extract(self, tr: Transcript, context: str) -> list[Promise]:
        spoken = time.strftime("%Y-%m-%d (%A)", time.localtime(tr.start_ts))
        messages = [
            {"role": "developer", "content": DEVELOPER_PROMPT},
            {"role": "user", "content": (
                f"[발화 날짜] {spoken}\n\n"
                f"[직전 문맥]\n{context or '(없음)'}\n\n"
                f"[판정 대상]\n{tr.text}"
            )},
        ]

        raw = self._call_with_retry(messages, tr.seq)
        if raw is None:
            return []

        out = []
        for item in _parse(raw, tr.seq):
            promise = self._build(tr, item)
            if promise is not None:
                out.append(promise)
        return out

    def _build(self, tr: Transcript, item: dict) -> Optional[Promise]:
        content = str(item.get("content", "")).strip()
        if not content:
            return None

        try:
            confidence = float(item.get("confidence", 0.0))
        except (TypeError, ValueError):
            confidence = 0.0
        if confidence < PROMISE_MIN_CONFIDENCE:
            return None

        # 기한은 LLM이 '며칠 뒤'로 알려주면 우리가 절대 시각으로 바꾼다.
        # 한국어 날짜 표현을 Spring이 파싱하게 두면 안 된다.
        deadline_at = None
        days = item.get("deadline_days")
        if isinstance(days, (int, float)) and 0 <= days <= 3650:
            deadline_at = tr.start_ts + float(days) * 86400

        # 기한 없는 약속은 버린다. 이 프로젝트는 '언제까지'가 붙은 약속만 다루고,
        # 날짜가 없으면 캘린더에도 미이행 판정에도 쓸 수 없다. 게이트에서 기한
        # 표현을 요구해도 LLM이 실제 날짜를 못 뽑는 경우가 남아 여기서 다시 막는다.
        if deadline_at is None:
            self.no_deadline += 1
            return None

        # 중복 판정은 마지막에 한다. 어차피 버릴 약속을 기록해두면 뒤에 오는
        # 같은 내용의 멀쩡한 약속이 중복으로 걸려 사라진다.
        if self._is_duplicate(tr.session_id, content, tr.start_ts):
            self.deduped += 1
            return None

        return Promise(
            seq=tr.seq,
            session_id=tr.session_id,
            content=content,
            raw_text=tr.text,
            deadline_text=_opt_str(item.get("deadline_text")),
            deadline_at=deadline_at,
            condition=_opt_str(item.get("condition")),
            confidence=confidence,
            spoken_at=tr.start_ts,
        )

    def _is_duplicate(self, session_id: str, content: str, ts: float) -> bool:
        key, _ = normalize(content)
        if not key:
            return False
        with self._seen_lock:
            seen = self._seen.setdefault(session_id, {})
            for old_key, old_ts in list(seen.items()):
                if ts - old_ts > PROMISE_DEDUP_SEC:
                    del seen[old_key]
            if key in seen:
                return True
            seen[key] = ts
            return False

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
                    log.error("약속 추출 포기 seq=%s: %s", seq, e)
                    break
                time.sleep(0.5)
        self.failed += 1
        return None


def _opt_str(value) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _parse(raw: str, seq: int) -> list[dict]:
    """LLM 응답을 안전하게 푼다. 깨져 있으면 빈 목록으로 떨어뜨린다."""
    try:
        data = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        log.warning("약속 JSON 파싱 실패 seq=%s raw=%r", seq, raw)
        return []
    items = data.get("promises")
    if not isinstance(items, list):
        return []
    return [i for i in items if isinstance(i, dict)]

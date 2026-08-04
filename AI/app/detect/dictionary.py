"""Aho-Corasick 사전매칭.

발화 하나를 받아 사전에 걸리는지 즉시 검사한다. 문자열 길이와 무관하게
한 번의 스캔으로 모든 단어를 동시에 찾으므로 지연은 사실상 0이다.
LLM을 기다릴 필요가 없는 명백한 비속어는 여기서 끝난다.

스트리머마다 사전이 다르다 (AI/RISK_POLICY_SPEC.md §1):

    스트리머 사전 = terms.yaml − 허용어 + 금칙어

허용어를 "매칭 후 걸러내기"가 아니라 "automaton에서 빼기"로 처리하는 것이 핵심이다.
빼두면 히트가 아예 나지 않아 server.py의 `if hits: ... elif _judge:` 분기가 그 발화를
자연스럽게 LLM으로 넘긴다. 분기 순서를 건드릴 필요가 없다.
"""

import functools
import logging
import re
from dataclasses import dataclass
from pathlib import Path

import ahocorasick
import yaml

from app.detect.normalize import normalize
from app.detect.personal import EMPTY, PersonalTerms

log = logging.getLogger(__name__)

DEFAULT_PATH = Path(__file__).with_name("terms.yaml")

# 개인 사전 automaton 캐시 상한. 동시에 방송하는 스트리머 수를 훨씬 넘는 값이면
# 충분하다 — 재접속·다중 세션이 같은 사전을 공유하게 하는 것이 목적이다.
_MATCHER_CACHE_SIZE = 256

_SEVERITY_RANK = {"low": 0, "medium": 1, "high": 2}

# 매칭된 욕설 바로 뒤에 붙는 인용·전언 어미.
# "병신이래요" "씨발이라고 한 사람" 처럼 남의 말을 옮기는 발화를 가려낸다.
#
# '이'를 선택으로 둔 이유: normalize가 "병신이래요"의 '이'를 늘여쓰기로 보고 지운다
# ('신'의 모음이 ㅣ라서 뒤따르는 'ㅇ+ㅣ'가 반복으로 처리된다). 그래서 규칙은
# "병신래요" 형태도 함께 받아야 한다.
#
# '대'만은 한 글자를 허용하지 않는다. 허용하면 "씨발대박" "좆대" "지랄대잔치"까지
# 인용으로 보고 놓친다.
_QUOTE_SUFFIX = re.compile(
    r"^(이?라고|이?래|이?랬|이?란다|이?라네|이?라던|이?라니|이?대요|이?댔)"
)


@dataclass
class Hit:
    term: str        # 사전의 대표 표기
    matched: str     # 원문에서 실제로 걸린 부분
    severity: str
    category: str
    start: int       # 원문 기준 인덱스
    end: int

    @property
    def rank(self) -> int:
        return _SEVERITY_RANK.get(self.severity, 0)


@functools.lru_cache(maxsize=1)
def _load_terms(path: str) -> dict:
    """terms.yaml 파싱 결과. 스트리머마다 matcher를 만들 때 매번 읽지 않게 캐시한다."""
    return yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}


class DictionaryMatcher:
    def __init__(self, path: Path | str = DEFAULT_PATH,
                 personal: PersonalTerms = EMPTY):
        data = _load_terms(str(path))

        # 허용어에 걸린 항목은 대표어·variants를 통째로 뺀다. 표기 하나만 빼면
        # "씨발"을 허용해도 "시발", "ㅅㅂ"이 그대로 걸려서, 스트리머가 같은 말의
        # 변형을 전부 등록해야 한다.
        allowed = set(personal.allow)

        self._auto = ahocorasick.Automaton()
        count = 0
        dropped = 0
        for entry in data.get("terms") or []:
            term = entry["term"]
            spellings = [term, *(entry.get("variants") or [])]
            normalized = [n for n, _ in (normalize(s) for s in spellings) if n]
            if allowed.intersection(normalized):
                dropped += 1
                continue

            meta = (term, entry.get("severity", "medium"),
                    entry.get("category", "profanity"))
            for norm in normalized:
                self._auto.add_word(norm, (norm, meta))
                count += 1

        # 금칙어는 기본 사전에 없던 표기다. 물려받을 category가 없어 profanity로 둔다 —
        # 스트리머에게 감지기 카테고리 7종을 고르라고 하는 것은 과하다.
        for norm, severity in personal.block:
            self._auto.add_word(norm, (norm, (norm, severity, "profanity")))
            count += 1

        self.size = count
        self.personal_block = len(personal.block)
        self.personal_allow_dropped = dropped
        if count:
            self._auto.make_automaton()

        self._allow = ahocorasick.Automaton()
        allow_count = 0
        for word in data.get("allowlist") or []:
            norm, _ = normalize(word)
            if norm:
                self._allow.add_word(norm, norm)
                allow_count += 1
        if allow_count:
            self._allow.make_automaton()
        self._has_allow = allow_count > 0

    def match(self, text: str) -> list[Hit]:
        """걸린 단어들을 원문 위치와 함께 돌려준다. 없으면 빈 리스트."""
        if not self.size or not text:
            return []
        norm, idx = normalize(text)
        if not norm:
            return []

        # "시발점" 같은 예외어 구간을 먼저 잡아둔다.
        blocked: list[tuple[int, int]] = []
        if self._has_allow:
            for end, word in self._allow.iter(norm):
                blocked.append((end - len(word) + 1, end + 1))

        hits: list[Hit] = []
        for end, (norm_term, (term, severity, category)) in self._auto.iter(norm):
            start = end - len(norm_term) + 1
            if any(bs <= start and end + 1 <= be for bs, be in blocked):
                continue
            o_start, o_end = idx[start], idx[end] + 1

            # 사전은 문맥을 모른다. 아래 두 경우는 단정하지 않고 버려서
            # server.py가 LLM 판정으로 넘기게 한다.
            if _misaligned_with_words(text, o_start, o_end):
                continue
            if _QUOTE_SUFFIX.match(norm[end + 1:]):
                continue

            hits.append(Hit(term=term, matched=text[o_start:o_end],
                            severity=severity, category=category,
                            start=o_start, end=o_end))

        return _drop_contained(hits)


def _misaligned_with_words(text: str, start: int, end: int) -> bool:
    """공백을 가로지르는 매칭이 어절과 정확히 맞아떨어지지 않으면 버린다.

    정규화가 공백을 지우는 탓에 "즉시 발동", "몇 시 발표"에서 '시발'이 걸린다.
    반대로 음성인식이 욕설을 통째로 띄어 놓는 일도 잦다 — Whisper 한국어 출력은
    "씨 발", "개 새끼"처럼 끊는 경우가 드물지 않아서, 예전처럼 공백이 있다는 이유만으로
    전부 버리면 가장 명백한 욕설을 놓친다.

    둘을 가르는 것은 **매칭이 어절과 정확히 겹치는가**다:

        "씨 발 뭐야"      |씨 발|이 어절 둘을 통째로 덮는다        → 받는다
        "즉시 발동"       즉|시 발|동, 양쪽 다 어절 중간         → 버린다
        "몇 시 발표였지"   |시 발|표, 시작은 맞지만 끝이 어절 중간   → 버린다

    시작만 보는 규칙으로는 마지막 예가 걸러지지 않는다. '시'는 시간 표현("몇 시",
    "세 시")에서 단독 어절로 자주 나오고 뒤에 '발-'로 시작하는 단어가 얼마든지 오므로
    (발표·발전·발음·발견·발생), allowlist로 막으려 들면 끝이 없다.

    대신 "개 새끼야"처럼 조사·어미가 붙은 형태는 여기서 버려진다. 이건 손실이 아니라
    이관이다 — 사전이 단정하지 않으면 server.py가 LLM 판정으로 넘기고, 그쪽이 문맥까지
    보고 잡는다(terms.yaml 설계 원칙 1과 같은 태도다).

    공백이 없는 매칭은 이 검사와 무관하다.
    """
    if not any(ch.isspace() for ch in text[start:end]):
        return False
    at_word_start = start == 0 or text[start - 1].isspace()
    at_word_end = end >= len(text) or text[end].isspace()
    return not (at_word_start and at_word_end)


def _drop_contained(hits: list[Hit]) -> list[Hit]:
    """다른 매칭에 완전히 포함된 것은 버린다. '개새끼'가 걸리면 '새끼'는 뺀다."""
    kept: list[Hit] = []
    for hit in sorted(hits, key=lambda h: (h.start, -(h.end - h.start))):
        if any(k.start <= hit.start and hit.end <= k.end for k in kept):
            continue
        kept.append(hit)
    return kept


@functools.lru_cache(maxsize=_MATCHER_CACHE_SIZE)
def _cached_matcher(personal: PersonalTerms) -> DictionaryMatcher:
    return DictionaryMatcher(personal=personal)


def matcher_for(personal: PersonalTerms) -> DictionaryMatcher:
    """스트리머 사전에 맞는 matcher를 돌려준다. 같은 사전이면 같은 객체를 재사용한다.

    PersonalTerms가 불변·해시 가능해서 그 자체가 캐시 키다. 별도 버전 번호를 관리할
    필요가 없고, 사전이 같은 재접속·다중 세션이 자연히 automaton을 공유한다.

    matcher는 만들어진 뒤 읽기만 하므로 여러 세션이 나눠 써도 안전하다.
    """
    if personal.is_empty:
        return BASE
    return _cached_matcher(personal)


# 기본 사전. 개인 사전이 없는 세션과, 세션이 이미 끊긴 뒤 도착한 전사가 쓴다.
BASE = DictionaryMatcher()

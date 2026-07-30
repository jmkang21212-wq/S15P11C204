"""Aho-Corasick 사전매칭.

발화 하나를 받아 사전에 걸리는지 즉시 검사한다. 문자열 길이와 무관하게
한 번의 스캔으로 모든 단어를 동시에 찾으므로 지연은 사실상 0이다.
LLM을 기다릴 필요가 없는 명백한 비속어는 여기서 끝난다.
"""

import re
from dataclasses import dataclass
from pathlib import Path

import ahocorasick
import yaml

from app.detect.normalize import normalize

DEFAULT_PATH = Path(__file__).with_name("terms.yaml")

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


class DictionaryMatcher:
    def __init__(self, path: Path | str = DEFAULT_PATH):
        data = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}

        self._auto = ahocorasick.Automaton()
        count = 0
        for entry in data.get("terms") or []:
            term = entry["term"]
            meta = (term, entry.get("severity", "medium"),
                    entry.get("category", "profanity"))
            for spelling in [term, *(entry.get("variants") or [])]:
                norm, _ = normalize(spelling)
                if norm:
                    self._auto.add_word(norm, (norm, meta))
                    count += 1
        self.size = count
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
            if _crosses_whitespace(text, o_start, o_end):
                continue
            if _QUOTE_SUFFIX.match(norm[end + 1:]):
                continue

            hits.append(Hit(term=term, matched=text[o_start:o_end],
                            severity=severity, category=category,
                            start=o_start, end=o_end))

        return _drop_contained(hits)


def _crosses_whitespace(text: str, start: int, end: int) -> bool:
    """매칭 구간이 원문의 띄어쓰기를 가로질렀는지 본다.

    정규화가 공백을 지우는 탓에 "즉시 발동", "동시 발사", "제시 발표"에서 '시발'이
    걸린다. 이런 단어 경계 매칭은 대부분 오탐이므로 사전이 단정하지 않는다.
    음성인식 결과라 "씨 발"처럼 띄어 발음되는 경우는 드물고, 있더라도 LLM이 잡는다.
    """
    return any(ch.isspace() for ch in text[start:end])


def _drop_contained(hits: list[Hit]) -> list[Hit]:
    """다른 매칭에 완전히 포함된 것은 버린다. '개새끼'가 걸리면 '새끼'는 뺀다."""
    kept: list[Hit] = []
    for hit in sorted(hits, key=lambda h: (h.start, -(h.end - h.start))):
        if any(k.start <= hit.start and hit.end <= k.end for k in kept):
            continue
        kept.append(hit)
    return kept

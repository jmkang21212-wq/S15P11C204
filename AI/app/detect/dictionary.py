"""Aho-Corasick 사전매칭.

발화 하나를 받아 사전에 걸리는지 즉시 검사한다. 문자열 길이와 무관하게
한 번의 스캔으로 모든 단어를 동시에 찾으므로 지연은 사실상 0이다.
LLM을 기다릴 필요가 없는 명백한 비속어는 여기서 끝난다.
"""

from dataclasses import dataclass
from pathlib import Path

import ahocorasick
import yaml

from app.detect.normalize import normalize

DEFAULT_PATH = Path(__file__).with_name("terms.yaml")

_SEVERITY_RANK = {"low": 0, "medium": 1, "high": 2}


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
            hits.append(Hit(term=term, matched=text[o_start:o_end],
                            severity=severity, category=category,
                            start=o_start, end=o_end))

        return _drop_contained(hits)


def _drop_contained(hits: list[Hit]) -> list[Hit]:
    """다른 매칭에 완전히 포함된 것은 버린다. '개새끼'가 걸리면 '새끼'는 뺀다."""
    kept: list[Hit] = []
    for hit in sorted(hits, key=lambda h: (h.start, -(h.end - h.start))):
        if any(k.start <= hit.start and hit.end <= k.end for k in kept):
            continue
        kept.append(hit)
    return kept

"""스트리머 개인 사전 (AI/RISK_POLICY_SPEC.md).

Spring이 티켓과 pub/sub으로 실어 보내는 금칙어·허용어를 담는다. 두 경로가 같은
JSON 모양을 쓰므로 파싱도 여기 한 곳에 둔다.

**받은 표기는 여기서 정규화한다.** Spring에도 정규화 구현이 있지만 그쪽은 등록 검증과
중복 판정용이고, 매칭의 정본은 이쪽이다. 그래서 Spring은 사용자 원문을 보내고 우리가
다시 정규화한다 — 두 구현이 어긋나도 매칭 결과는 항상 이 파일을 따라간다.
"""

import json
import logging
from dataclasses import dataclass
from typing import Iterable

from app.detect.normalize import normalize

log = logging.getLogger(__name__)

_SEVERITIES = ("low", "medium", "high")
_DEFAULT_SEVERITY = "medium"

# Spring이 상한을 강제하지만(종류당 200개) 여기서도 자른다. Redis 값은 누구든 쓸 수
# 있고, automaton 빌드 비용과 프롬프트 길이가 여기에 직접 걸린다.
MAX_PER_KIND = 200
MAX_TERM_LENGTH = 40
MIN_TERM_LENGTH = 2


@dataclass(frozen=True)
class PersonalTerms:
    """한 스트리머의 개인 사전. 캐시 키로 쓰려고 불변·해시 가능하게 둔다.

    block: (정규화된 표기, severity) 쌍들. 사전에 추가된다.
    allow: 정규화된 표기들. 사전에서 빠지고 LLM 프롬프트에도 주입된다.
    allow_display: 사용자 원문. LLM 프롬프트에는 이쪽을 넣는다 — 정규화된 표기는
                   사람이 읽는 문장이 아니라서 모델이 문맥을 잡기 어렵다.
    """

    block: tuple[tuple[str, str], ...] = ()
    allow: tuple[str, ...] = ()
    allow_display: tuple[str, ...] = ()

    @property
    def is_empty(self) -> bool:
        return not self.block and not self.allow

    @classmethod
    def parse(cls, data: object) -> "PersonalTerms":
        """티켓·pub/sub의 terms 객체를 읽는다. 이상하면 빈 사전으로 떨어진다.

        사전을 못 읽었다고 접속을 막지는 않는다 — 기본 감지는 사전 없이도 동작하고,
        방송을 시작하지 못하게 하는 쪽이 훨씬 나쁘다.
        """
        if not isinstance(data, dict):
            return cls()

        block: list[tuple[str, str]] = []
        seen_block: set[str] = set()
        for item in _limited(data.get("block"), "block"):
            if not isinstance(item, dict):
                continue
            term = _clean(item.get("term"))
            if term is None or term in seen_block:
                continue
            severity = str(item.get("severity", "")).lower()
            if severity not in _SEVERITIES:
                severity = _DEFAULT_SEVERITY
            seen_block.add(term)
            block.append((term, severity))

        allow: list[str] = []
        allow_display: list[str] = []
        seen_allow: set[str] = set()
        for item in _limited(data.get("allow"), "allow"):
            if not isinstance(item, str):
                continue
            term = _clean(item)
            if term is None or term in seen_allow:
                continue
            seen_allow.add(term)
            allow.append(term)
            # 원문은 프롬프트에 그대로 들어간다. 정규화가 공백·문장부호를 삼키므로
            # term이 짧아도 원문은 얼마든지 길 수 있다 — 자르지 않으면 200개만 모여도
            # LLM 입력이 터진다.
            allow_display.append(item.strip()[:MAX_TERM_LENGTH])

        return cls(tuple(block), tuple(allow), tuple(allow_display))

    @classmethod
    def parse_json(cls, raw: str | bytes) -> "PersonalTerms":
        """pub/sub 메시지 전체(JSON)에서 terms를 꺼낸다."""
        try:
            text = raw.decode() if isinstance(raw, bytes) else raw
            data = json.loads(text)
        except (json.JSONDecodeError, UnicodeDecodeError, TypeError, AttributeError):
            log.warning("개인 사전 메시지를 읽을 수 없다: %r", raw)
            return cls()
        if not isinstance(data, dict):
            return cls()
        return cls.parse(data.get("terms"))


EMPTY = PersonalTerms()


def _clean(value: object) -> str | None:
    """정규화하고 길이를 확인한다. 쓸 수 없으면 None.

    두 글자 하한은 terms.yaml 설계 원칙 3과 같은 이유다 — 한 글자는 다른 단어에 묻혀
    오탐만 만든다. Spring이 이미 막지만 Redis를 통해 들어오는 값이라 다시 본다.
    """
    if not isinstance(value, str):
        return None
    norm, _ = normalize(value)
    if len(norm) < MIN_TERM_LENGTH or len(norm) > MAX_TERM_LENGTH:
        return None
    return norm


def _limited(items: object, label: str) -> Iterable:
    if not isinstance(items, list):
        return ()
    if len(items) > MAX_PER_KIND:
        log.warning("개인 사전 %s가 상한(%d)을 넘어 잘랐다: %d개",
                    label, MAX_PER_KIND, len(items))
        return items[:MAX_PER_KIND]
    return items

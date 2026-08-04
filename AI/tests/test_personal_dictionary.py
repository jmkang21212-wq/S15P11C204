"""스트리머 개인 사전 (AI/RISK_POLICY_SPEC.md).

기본 사전을 건드리지 않으면서 스트리머마다 다르게 동작하는지 확인한다.
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.detect.dictionary import BASE, DictionaryMatcher, matcher_for
from app.detect.personal import MAX_PER_KIND, PersonalTerms


def terms(block=(), allow=()):
    return PersonalTerms.parse({
        "block": [{"term": t, "severity": s} for t, s in block],
        "allow": list(allow),
    })


def matched(matcher, text):
    return [hit.term for hit in matcher.match(text)]


# --- 금칙어 ---

def test_금칙어를_등록하면_기본_사전에_없던_표기가_걸린다() -> None:
    """'개새기'는 개새끼의 variants에 없고 정규화도 기·끼를 잇지 않아 기본 사전을 통과한다.
    이런 미탐을 스트리머가 직접 메우는 것이 금칙어의 용도다."""
    assert matched(BASE, "이 개새기야") == []

    matcher = matcher_for(terms(block=[("개새기", "HIGH")]))
    assert matched(matcher, "이 개새기야") == ["개새기"]


def test_금칙어의_등급이_경고에_실린다() -> None:
    matcher = matcher_for(terms(block=[("개새기", "HIGH")]))
    assert matcher.match("이 개새기야")[0].severity == "high"


def test_금칙어_등급을_생략하면_medium이다() -> None:
    matcher = matcher_for(PersonalTerms.parse({"block": [{"term": "개새기"}]}))
    assert matcher.match("이 개새기야")[0].severity == "medium"


def test_금칙어도_정규화_변형을_함께_잡는다() -> None:
    """등록은 한 번만 해도 늘여쓰기·띄어쓰기 변형이 따라온다."""
    matcher = matcher_for(terms(block=[("개새기", "MEDIUM")]))
    assert matched(matcher, "이 개새애기야") == ["개새기"]


def test_금칙어도_기존_오탐_방어를_물려받는다() -> None:
    """match() 안의 인용 어미 방어가 개인 사전에도 그대로 적용된다.
    남의 말을 옮기는 발화는 사전이 단정하지 않고 LLM으로 넘긴다."""
    matcher = matcher_for(terms(block=[("개새기", "HIGH")]))
    assert matched(matcher, "누가 저보고 개새기래요") == []


# --- 허용어 ---

def test_허용어는_사전에서_빠진다() -> None:
    assert matched(BASE, "아 씨발 뭐야") == ["씨발"]

    matcher = matcher_for(terms(allow=["씨발"]))
    assert matched(matcher, "아 씨발 뭐야") == []


def test_허용어는_같은_말의_변형까지_함께_뺀다() -> None:
    """표기 하나만 빼면 스트리머가 '시발', 'ㅅㅂ'을 전부 등록해야 한다.
    대표어든 variants든 하나가 걸리면 그 항목을 통째로 뺀다."""
    matcher = matcher_for(terms(allow=["씨발"]))
    for variant in ("아 시발 뭐야", "아 ㅅㅂ 뭐야", "아 씨이이발 뭐야"):
        assert matched(matcher, variant) == [], variant


def test_허용어는_다른_단어를_건드리지_않는다() -> None:
    matcher = matcher_for(terms(allow=["씨발"]))
    assert matched(matcher, "이 병신아") == ["병신"]


def test_사전에_없는_표현을_허용어로_넣어도_사전은_그대로다() -> None:
    """'밤에 혼자 뭐 하세요'는 LLM이 문맥으로 잡는 케이스라 automaton에는 영향이 없다.
    이런 항목은 프롬프트로만 작동한다."""
    matcher = matcher_for(terms(allow=["밤에 혼자 뭐 하세요"]))
    assert matcher.size == BASE.size
    assert matched(matcher, "아 씨발 뭐야") == ["씨발"]


# --- 기본 사전 보호 ---

def test_개인_사전이_기본_사전을_오염시키지_않는다() -> None:
    """matcher를 스트리머마다 새로 만들므로 다른 방송에 영향이 없어야 한다."""
    matcher_for(terms(block=[("개새기", "HIGH")], allow=["씨발"]))

    assert matched(BASE, "아 씨발 뭐야") == ["씨발"]
    assert matched(BASE, "이 개새기야") == []


def test_빈_사전은_기본_사전을_그대로_쓴다() -> None:
    assert matcher_for(PersonalTerms()) is BASE


def test_같은_사전은_automaton을_재사용한다() -> None:
    """재접속·다중 세션마다 automaton을 다시 빌드하지 않는다."""
    first = matcher_for(terms(block=[("개새기", "HIGH")]))
    second = matcher_for(terms(block=[("개새기", "HIGH")]))
    assert first is second


# --- 입력 방어 ---

@pytest.mark.parametrize("bad", ["", " ", "!", "아", "아!"])
def test_두_글자_미만은_버린다(bad: str) -> None:
    """한 글자는 다른 단어에 묻힌다. '아!'처럼 정규화 후 한 글자가 되는 것도 막는다."""
    assert PersonalTerms.parse({"block": [{"term": bad}], "allow": [bad]}).is_empty


def test_너무_긴_표기는_버린다() -> None:
    # 같은 글자를 반복하면 정규화가 2글자로 줄여버려 길이 검증을 통과한다.
    # 상한을 실제로 넘기려면 서로 다른 글자여야 한다.
    long_term = "".join(chr(0xAC00 + i) for i in range(41))
    assert PersonalTerms.parse({"allow": [long_term]}).is_empty


def test_프롬프트에_들어가는_원문도_길이를_자른다() -> None:
    """정규화가 대부분을 삼키면 term은 짧아도 원문은 얼마든지 길 수 있다.
    그 원문이 그대로 프롬프트에 들어가면 200개만 모여도 입력이 터진다."""
    parsed = PersonalTerms.parse({"allow": ["가" * 400]})
    assert parsed.allow == ("가가",)
    assert len(parsed.allow_display[0]) <= 40


def test_상한을_넘으면_잘라낸다() -> None:
    """Spring이 막지만 Redis 값은 누구든 쓸 수 있어 여기서도 자른다."""
    many = [f"금칙어{i:03d}" for i in range(MAX_PER_KIND + 50)]
    parsed = PersonalTerms.parse({"block": [{"term": t} for t in many]})
    assert len(parsed.block) == MAX_PER_KIND


def test_중복_등록은_한_번만_남는다() -> None:
    parsed = PersonalTerms.parse({"allow": ["씨발", "씨이이발", "씨 발"]})
    assert parsed.allow == ("씨발",)


@pytest.mark.parametrize("garbage", [None, [], "문자열", {"block": "리스트가 아님"}])
def test_망가진_입력은_빈_사전이_된다(garbage: object) -> None:
    """사전을 못 읽었다고 접속을 막지는 않는다. 기본 감지는 사전 없이도 동작한다."""
    assert PersonalTerms.parse(garbage).is_empty


def test_pubsub_메시지에서_사전을_꺼낸다() -> None:
    parsed = PersonalTerms.parse_json(
        '{"block": [], "terms": {"allow": ["씨발"]}}')
    assert parsed.allow == ("씨발",)


def test_망가진_pubsub_메시지는_빈_사전이_된다() -> None:
    assert PersonalTerms.parse_json("not json").is_empty


# --- 원문 보존 ---

def test_프롬프트에는_사용자_원문을_넘긴다() -> None:
    """정규화된 표기는 사람이 읽는 문장이 아니라 모델이 문맥을 잡기 어렵다."""
    parsed = PersonalTerms.parse({"allow": ["밤에 혼자 뭐 하세요"]})
    assert parsed.allow == ("밤에혼자뭐하세요",)
    assert parsed.allow_display == ("밤에 혼자 뭐 하세요",)


def test_기본_사전_파일은_한_번만_읽는다() -> None:
    """스트리머마다 matcher를 만들 때 yaml을 매번 파싱하면 접속이 느려진다."""
    from app.detect.dictionary import _load_terms

    before = _load_terms.cache_info().misses
    DictionaryMatcher()
    DictionaryMatcher()
    assert _load_terms.cache_info().misses == before

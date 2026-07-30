"""Spring으로 나가는 위험 경고 payload가 DB 제약을 통과하는지 검증한다.

감지기와 Spring의 어휘가 달라, 변환 없이 보내면 Spring의
ck_risk_events_risk_category / ck_risk_events_risk_level 위반으로 INSERT가 실패한다.
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.detect.dictionary import DictionaryMatcher
from app.sink.spring import _risk_category

# BackEnd/spring/laive/src/main/resources/db/migration/V1__init_core_schema.sql
SPRING_CATEGORIES = {
    "PROFANITY", "POLITICS", "DISCRIMINATION", "SEXUAL", "DEFAMATION", "OTHER",
}
SPRING_LEVELS = {"HIGH", "MEDIUM", "LOW"}

# app/detect/llm_judge.py의 DEVELOPER_PROMPT가 정의한 위험 유형
LLM_CATEGORIES = ["profanity", "insult", "threat", "harassment", "discrimination"]


@pytest.mark.parametrize("category", LLM_CATEGORIES)
def test_LLM_카테고리는_모두_Spring_값으로_변환된다(category: str) -> None:
    assert _risk_category(category) in SPRING_CATEGORIES


def test_사전_카테고리는_모두_Spring_값으로_변환된다() -> None:
    """terms.yaml에 실제로 쓰인 카테고리를 훑는다. 새 카테고리를 추가하면 여기서 걸린다."""
    matcher = DictionaryMatcher()
    samples = [
        "아 씨발", "이 개새끼가", "야 병신아", "지랄하지 마",
        "닥쳐 좀", "등신같이", "죽여버린다", "미친놈이네",
    ]
    seen = {h.category for s in samples for h in matcher.match(s)}
    assert seen, "샘플이 하나도 안 걸렸다. 사전이 비었는지 확인하라."
    for category in seen:
        assert _risk_category(category) in SPRING_CATEGORIES, category


def test_사전_severity는_모두_Spring_등급이다() -> None:
    matcher = DictionaryMatcher()
    samples = ["아 씨발", "지랄하지 마", "닥쳐 좀", "죽여버린다"]
    seen = {h.severity.upper() for s in samples for h in matcher.match(s)}
    assert seen
    assert seen <= SPRING_LEVELS, seen


def test_모르는_카테고리는_OTHER로_보낸다() -> None:
    assert _risk_category("aggressive") == "OTHER"
    assert _risk_category("무언가새로운값") == "OTHER"
    assert _risk_category("") == "OTHER"
    assert _risk_category(None) == "OTHER"


def test_대소문자를_가리지_않는다() -> None:
    assert _risk_category("PROFANITY") == "PROFANITY"
    assert _risk_category("Profanity") == "PROFANITY"

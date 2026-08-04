"""LLM 판정이 알림으로 나갈지 결정하는 기준 테스트.

실제 방송에서 평범한 대화에 경고가 붙던 사례를 회귀로 고정한다.
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.detect.llm_judge import Verdict


def verdict(risk: str, category: str) -> Verdict:
    return Verdict(
        seq=0, text="", start_ts=0.0, end_ts=0.0,
        risk=risk, category=category, reason="", latency=0.0,
    )


@pytest.mark.parametrize("risk,category,note", [
    ("none", "none", "평범한 발화"),
    # category가 none이면 등급과 무관하게 위험이 아니다.
    # LLM이 "위험 표현 없음"이라 해놓고 risk만 올려 답하는 모순 판정을 막는다.
    ("low", "none", "상대방을 피해"),
    ("medium", "none", "모순 판정"),
    ("high", "none", "모순 판정"),
    # LLM_ALERT_MIN_RISK가 medium이라 low는 판정이 맞아도 알리지 않는다.
    # 경고가 흔해지면 스트리머가 경고를 보지 않게 되는 쪽이 더 나쁘다(config.py).
    # 사전 경로는 이 임계값과 무관하므로 `닥쳐`(terms.yaml에서 low)는 그대로 알림이 간다.
    ("low", "aggressive", "닥쳐·꺼져 수준의 거친 말"),
    ("low", "profanity", "가벼운 비속어"),
    ("low", "threat", "때릴 거야 수준의 으름장"),
])
def test_알림을_보내지_않는다(risk: str, category: str, note: str) -> None:
    assert not verdict(risk, category).is_risky, note


@pytest.mark.parametrize("risk,category,note", [
    ("medium", "insult", "분명한 모욕"),
    ("medium", "profanity", "분명한 욕설"),
    ("medium", "sexual", "성드립"),
    ("high", "profanity", "심한 욕설"),
    ("high", "threat", "직접적 위협"),
    ("high", "sexual", "노골적 성적 표현"),
    ("high", "harassment", "성희롱"),
    ("medium", "discrimination", "차별 발언"),
])
def test_알림을_보낸다(risk: str, category: str, note: str) -> None:
    assert verdict(risk, category).is_risky, note


def test_알_수_없는_등급은_알리지_않는다() -> None:
    assert not verdict("critical", "profanity").is_risky
    assert not verdict("", "profanity").is_risky

"""프론트로 나가는 WebSocket 메시지가 FRONTEND_API.md 규격과 맞는지 검증한다.

Spring 연동과 무관하게 동작해야 한다. 실제로 SPRING_BASE_URL이 비어 있으면
server.py는 Spring 전송만 건너뛰고 session.push는 그대로 한다.
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.detect.dictionary import DictionaryMatcher
from app.detect.llm_judge import Verdict

# FRONTEND_API.md 4절이 약속한 값
DOC_SEVERITY = {"high", "medium", "low"}
DOC_CATEGORY = {"profanity", "insult", "threat", "aggressive",
                "sexual", "harassment", "discrimination"}


@pytest.fixture(scope="module")
def matcher() -> DictionaryMatcher:
    return DictionaryMatcher()


def build_dictionary_alert(text: str, matcher: DictionaryMatcher) -> dict:
    """server.py의 _dispatch_transcripts와 같은 방식으로 payload를 만든다."""
    hits = matcher.match(text)
    if not hits:
        return {}
    worst = max(hits, key=lambda h: h.rank)
    return {
        "type": "alert",
        "source": "dictionary",
        "seq": 12,
        "severity": worst.severity,
        "category": worst.category,
        "text": text,
        "start_ts": 0.0,
        "end_ts": 1.0,
        "hits": [
            {"matched": h.matched, "severity": h.severity,
             "category": h.category, "start": h.start, "end": h.end}
            for h in hits
        ],
    }


def test_사전_경고_payload가_규격을_지킨다(matcher: DictionaryMatcher) -> None:
    alert = build_dictionary_alert("아 씨발 병신아", matcher)
    assert alert, "샘플이 사전에 안 걸렸다"

    for field in ("type", "source", "seq", "severity", "category",
                  "text", "start_ts", "end_ts", "hits"):
        assert field in alert, field

    assert alert["type"] == "alert"
    assert alert["source"] == "dictionary"
    assert alert["severity"] in DOC_SEVERITY
    assert alert["category"] in DOC_CATEGORY
    assert isinstance(alert["hits"], list) and alert["hits"]


def test_hits의_인덱스로_원문을_잘라낼_수_있다(matcher: DictionaryMatcher) -> None:
    """FRONTEND_API.md: hits[].start/end는 text 안의 문자 인덱스다."""
    text = "아 씨발 병신아"
    alert = build_dictionary_alert(text, matcher)
    for hit in alert["hits"]:
        assert text[hit["start"]:hit["end"]] == hit["matched"]


def test_사전이_내보내는_모든_카테고리가_문서에_있다(matcher: DictionaryMatcher) -> None:
    """terms.yaml에 문서에 없는 category를 넣으면 프론트가 모르는 값을 받는다."""
    samples = [
        "아 씨발", "이 개새끼가", "야 병신아", "지랄하지 마",
        "닥쳐 좀", "등신같이", "죽여버린다", "미친놈이네", "좆같네",
        "섹스 얘기 그만", "빠구리 어쩌고", "야동 봤냐", "섹드립 치네",
    ]
    seen = {h.category for s in samples for h in matcher.match(s)}
    assert seen
    unknown = seen - DOC_CATEGORY
    assert not unknown, f"FRONTEND_API.md에 없는 category: {unknown}"


def test_사전이_내보내는_모든_severity가_문서에_있다(matcher: DictionaryMatcher) -> None:
    samples = ["아 씨발", "지랄하지 마", "닥쳐 좀", "죽여버린다", "등신같이", "야동 봤냐"]
    seen = {h.severity for s in samples for h in matcher.match(s)}
    assert seen
    assert seen <= DOC_SEVERITY, seen


def test_LLM_경고는_none만_걸러진다() -> None:
    """등급은 '위험한지'가 아니라 '얼마나 심한지'다. 위험이 아니라는 판단은 none이 받는다."""
    def v(risk: str, category: str) -> Verdict:
        return Verdict(seq=0, text="", start_ts=0.0, end_ts=0.0,
                       risk=risk, category=category, reason="", latency=0.0)

    assert v("high", "profanity").is_risky
    assert v("medium", "insult").is_risky
    assert v("low", "aggressive").is_risky
    assert v("high", "sexual").is_risky

    assert not v("none", "none").is_risky
    # category가 none이면 등급과 무관하게 위험이 아니다
    assert not v("low", "none").is_risky
    assert not v("high", "none").is_risky

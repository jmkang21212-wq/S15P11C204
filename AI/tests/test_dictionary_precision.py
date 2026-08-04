"""사전 정밀도 회귀 테스트.

사전을 손볼 때마다 오탐이 늘지 않는지 확인한다. 위험 발언 브리핑은 스트리머가
방송 중에 보는 알림이라, 놓치는 것보다 헛방이 잦은 쪽이 훨씬 성가시다.
그래서 오탐 0건을 기준으로 잡는다.
"""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.detect.dictionary import DictionaryMatcher

# 사전이 절대 잡으면 안 되는 정상 발화
CLEAN = [
    # 게임 방송 감탄·강조
    "이번 판 미친 각도로 들어갔다 진짜",
    "얘 실력 미친 거 아니야?",
    "미친 이걸 살리네",
    "개잘하네 진짜",
    "개꿀잼이다 오늘",
    # 띄어쓰기가 지워지며 단어 경계를 넘는 경우
    "즉시 발동되는 스킬이야",
    "동시 발사 가능해",
    "다시 발동시켜봐",
    "몇 시 발표였지",
    "잠시 발 좀 뺄게",
    "제시 발표 자료 봤어?",
    # 어절 첫머리에서 시작하지만 끝이 어절 중간인 경우. '시'는 시간 표현에서 단독
    # 어절로 흔하고 뒤에 '발-' 단어가 얼마든지 온다 — 시작만 보는 규칙으로는 못 막는다.
    "몇 시 발전소 견학이었지",
    "지금 시 발음이 이상하게 들려",
    "이 병 신경 써서 골랐어",
    "저 병 신문에 나왔던 거야",
    # 어미·활용형에 묻히는 경우
    "저기 잘 안 보지? 화면 못 보지",
    "빨리 가자지 뭐 하자지",
    "치킨 시키자 배달 시키고 있어",
    "인터넷 좀 뒤져봐야겠다",
    "서랍 뒤져보니까 있더라",
    "불 좀 꺼져 있어서 화면이 안 보이네",
    "컴퓨터가 갑자기 꺼졌어",
    # 복합어에 묻히는 경우
    "새끼 고양이 키우고 있어요",
    "새끼손가락 걸고 약속",
    "강아지 새끼 진짜 귀엽다",
    "눈깔사탕 먹고 싶다",
    "등신대 세워놨더라",
    "시발점이 어디였더라",
    "십장생 그림 봤어",
    "가스발전소 견학 갔다 왔어",
    "질병시 대응 매뉴얼 확인",
    "허리띠 발견했다",
    # 고유명사·외래어
    "세키로 할 사람 있나요",
    "3개년 계획 세웠거든",
    "호모 사피엔스 얘기하는 거야",
    "데이터 analysis 돌리는 중",
    "체스에서 c8 칸으로 이동",
    # 일상 표현
    "망할 뻔했는데 살았다",
    "구라 아니고 진짜야",
    "오늘 방송 허접했죠 죄송",
    "아 짜증나 진짜 안 되네",
    # 성적 표현과 충돌하는 정상 발화. 아래 단어들을 사전에 넣으면 전부 오탐이 된다.
    "자위대 관련 뉴스 봤어",
    "정당방위 자위 개념이야",
    "김성기 선수 잘하네",
    "성기사 직업 골랐어",
    "오후장 주가 올랐네",
    "고양이 발정기라서 시끄러워",
    "불알친구랑 만났어",
    "잠지 말고 일어나",
    "꼴리는 대로 골라",
    "가슴이 두근거린다",
    "엉덩이 아프네 의자가",
    "인절미 떡 치기 힘들다",
    "오랄비 칫솔 샀어",
]

# 문맥 없이도 욕설이라 사전이 반드시 잡아야 하는 발화
DIRTY = [
    "아 씨발 뭐야 이게",
    "이 개새끼가 진짜",
    "야 병신아 그것도 못하냐",
    "씨팔 또 죽었네",
    "ㅅㅂ 진짜 안 되네",
    "이 씹새끼 진짜 어이없네",
    "죽여버린다 진짜",
    "너 뒤질래?",
    "쒸발 이게 왜 이래",
    "씌팔 진짜 짜증나",
    "개색끼가 어디서",
    "븅신같이 왜 그래",
    "지랄하지 마라",
    "닥쳐 좀",
    "미친놈이네 진짜",
    # 음성인식이 욕설을 띄어 놓은 경우. Whisper 한국어 출력에서 드물지 않다.
    # 매칭이 어절과 정확히 겹치므로 사전이 단정한다 (dictionary.py `_misaligned_with_words`).
    "씨 발 뭐야 이게",
    "아 씨 발",
    "개 새끼 진짜",
    "병 신 아니야?",
    "죽여 버린다 진짜",
    # 인용 어미 규칙이 과하게 억제하면 안 되는 것들.
    # '대' 한 글자를 인용으로 보면 아래가 전부 미탐이 된다.
    "씨발대박 이걸 이기네",
    "좆대따 진짜",
    "지랄대잔치 하고 있네",
    "병신대회 나가라",
    # 성적 표현
    "섹스 얘기는 그만하자",
    "빠구리 어쩌고 하지 마",
    "딸딸이 얘기 왜 나와",
    "야동 봤냐고 묻지 마",
    "섹드립 치지 마라",
]

# 욕설을 '언급'만 하거나 남의 말을 옮기는 발화.
# 사전이 단정하면 안 되고 LLM 문맥 판정으로 넘어가야 한다.
META_AND_QUOTED = [
    "욕설 감지 잘 되고 있어",
    "님들 욕 하지마세요",
    "채팅에 욕 쓰지 말아주세요",
    "누가 저보고 병신이래요",
    "채팅에 씨발이라고 쓴 사람 누구야",
    "아까 개새끼라고 한 사람 나가주세요",
    "병신이래 참나",
    "병신이랬어 방금",
    "병신이라던데 진짜야?",
    "씨발이라니 너무하네",
    # 인용형을 variants에 넣으면 인용 억제 규칙이 무력화된다.
    # ("닥치라고"를 통째로 등록하면 매칭 뒤에 검사할 어미가 남지 않는다)
    "아 이 미녀가 닥치라고 했잖아 내가",
    "내가 닥치라고 말했어",
    "누가 꺼지라고 했어?",
]


@pytest.fixture(scope="module")
def matcher() -> DictionaryMatcher:
    return DictionaryMatcher()


@pytest.mark.parametrize("text", CLEAN)
def test_정상_발화는_잡지_않는다(matcher: DictionaryMatcher, text: str) -> None:
    hits = matcher.match(text)
    assert hits == [], f"오탐: {[h.matched for h in hits]}"


@pytest.mark.parametrize("text", DIRTY)
def test_명백한_욕설은_잡는다(matcher: DictionaryMatcher, text: str) -> None:
    assert matcher.match(text), "미탐"


@pytest.mark.parametrize("text", META_AND_QUOTED)
def test_언급과_인용은_LLM으로_넘긴다(matcher: DictionaryMatcher, text: str) -> None:
    hits = matcher.match(text)
    assert hits == [], f"사전이 단정하면 안 됨: {[h.matched for h in hits]}"


def test_사전에_인용형을_등록하지_않는다() -> None:
    """term이나 variants에 인용 어미가 붙은 형태가 들어오면 안 된다.

    "닥치라고"처럼 인용형을 통째로 등록하면 매칭 뒤에 검사할 어미가 남지 않아
    dictionary.py의 인용 억제 규칙이 그대로 무력화된다.
    """
    import re

    import yaml

    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "app", "detect", "terms.yaml")
    data = yaml.safe_load(open(path, encoding="utf-8"))
    quote = re.compile(r"(라고|이래|이랬|이란다|이라네|이라던|이라니|대요|랬어|댔)")

    found = [
        (entry["term"], spelling)
        for entry in data["terms"]
        for spelling in [entry["term"], *(entry.get("variants") or [])]
        if quote.search(spelling)
    ]
    assert not found, f"인용형이 등록되어 있다: {found}"


def test_정밀도와_재현율(matcher: DictionaryMatcher) -> None:
    """개별 케이스가 아니라 전체 지표로도 확인한다."""
    false_positives = sum(1 for t in [*CLEAN, *META_AND_QUOTED] if matcher.match(t))
    true_positives = sum(1 for t in DIRTY if matcher.match(t))

    precision = true_positives / (true_positives + false_positives)
    recall = true_positives / len(DIRTY)

    assert precision == 1.0, f"정밀도 {precision:.0%} (오탐 {false_positives}건)"
    assert recall == 1.0, f"재현율 {recall:.0%} (미탐 {len(DIRTY) - true_positives}건)"

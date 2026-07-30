"""/summary 서비스 단위 테스트 — 모델·Redis·LLM 없이 실행 (fakeredis + 가짜 llm_fn).

    python tests/test_summary.py     또는     python -m pytest tests/ -q
"""
import os
import sys

import fakeredis

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.reaction import is_reaction
from chatai.summary import SummaryRejected, SummaryService
from chatai.window import BroadcastWindow

T0 = 1_700_000_000_000

# LABELS 순서: 웃음0 환호1 응원2 놀람3 슬픔4 분노5 혼란6 중립7
P = {
    '웃음/재미': [1.0, 0, 0, 0, 0, 0, 0, 0],
    '환호/감탄': [0, 1.0, 0, 0, 0, 0, 0, 0],
    '분노/불만': [0, 0, 0, 0, 0, 1.0, 0, 0],
    '혼란/질문': [0, 0, 0, 0, 0, 0, 1.0, 0],
    '중립':     [0, 0, 0, 0, 0, 0, 0, 1.0],
}


class FakeLLM:
    def __init__(self, reply='요약 결과입니다.', fail=False):
        self.reply = reply
        self.fail = fail
        self.calls = []                     # 받은 프롬프트 기록

    def __call__(self, prompt):
        self.calls.append(prompt)
        if self.fail:
            raise RuntimeError('LLM down')
        return self.reply


def setup(chats=None, viewers=500, bid='b1'):
    """fakeredis에 윈도우를 채우고 (service, llm, redis) 반환.
    chats: [(uid, msg, label)] — T0에 한 배치로 적재"""
    r = fakeredis.FakeRedis()
    llm = FakeLLM()
    svc = SummaryService(r, llm)
    if chats is not None:
        items = [(uid, msg, label, P[label]) for uid, msg, label in chats]
        BroadcastWindow(r, bid).add_batch(viewers, items, T0)
    return svc, llm, r


def fill(n_laugh=40, n_neutral=0):
    """웃음 n건(내용 채팅) + 중립 n건 구성"""
    chats = [(f'u{i}', f'개웃기네 상황{i}', '웃음/재미') for i in range(n_laugh)]
    chats += [(f'n{i}', f'지금 보스전 {i}페이즈', '중립') for i in range(n_neutral)]
    return chats


def expect_reject(svc, status, reason, **kw):
    try:
        svc.generate('b1', 'AUTO', **kw)
        raise AssertionError(f'{status} {reason} 거절이 나와야 함')
    except SummaryRejected as e:
        assert e.status == status, (e.status, e.payload)
        assert e.payload['reason'] == reason, e.payload
        return e.payload


def test_reaction_filter():
    for t in ['ㅋㅋㅋ', '캬ㅑㅑㅑ', '네ㅔㅔ', 'ㄷㄷ', 'ㅠㅠ', '!?', '앙~']:
        assert is_reaction(t), t
    for t in ['개웃기네', '왜 화면 멈춰요?', '지금 보스전 2페이즈', '가즈아']:
        assert not is_reaction(t), t


def test_unknown_broadcast_404():
    svc, _, _ = setup(chats=None)
    expect_reject(svc, 404, 'UNKNOWN_BROADCAST')


def test_not_enough_chats_422():
    svc, llm, _ = setup(chats=fill(n_laugh=10))
    payload = expect_reject(svc, 422, 'NOT_ENOUGH_CHATS', now_ms=T0 + 1000)
    assert payload['chatCount'] == 10 and payload['required'] == 30
    assert not llm.calls, 'LLM이 호출되면 안 됨'


def test_success_response_schema():
    svc, llm, _ = setup(chats=fill(n_laugh=40))
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    for field in ('summary', 'dominant', 'distribution', 'chatCount',
                  'windowSec', 'generatedAt', 'nextAvailableAt'):
        assert field in res, field
    assert res['summary'] == '요약 결과입니다.'
    assert res['dominant'] == '웃음/재미'
    assert len(llm.calls) == 1
    assert not any(k.startswith('_') for k in res), '내부 필드 노출 금지'


def test_cooldown_409():
    svc, llm, _ = setup(chats=fill(n_laugh=40))
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    payload = expect_reject(svc, 409, 'COOLDOWN', now_ms=T0 + 21_000)   # 20초 뒤
    assert 35 <= payload['retryAfterSec'] <= 40, payload
    assert 'lastSummaryAt' in payload
    assert len(llm.calls) == 1, '쿨다운 중 LLM 재호출 금지'
    # 60초 지나면 다시 생성
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 62_000)
    assert len(llm.calls) == 2 and res['summary']


def test_in_flight_409():
    svc, llm, r = setup(chats=fill(n_laugh=40))
    r.set('chat:b1:lock', '1')              # 다른 요청이 진행 중인 상태 재현
    expect_reject(svc, 409, 'IN_FLIGHT', now_ms=T0 + 1000)
    assert not llm.calls


def test_llm_failure_releases_lock():
    svc, llm, r = setup(chats=fill(n_laugh=40))
    llm.fail = True
    try:
        svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
        raise AssertionError('LLM 예외가 전파돼야 함')
    except RuntimeError:
        pass
    assert not r.exists('chat:b1:lock'), '실패해도 락은 풀려야 함'
    llm.fail = False
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 2000)   # 즉시 재시도 가능
    assert res['summary']


def test_get_cached():
    svc, _, _ = setup(chats=fill(n_laugh=40))
    assert svc.get_cached('b1') is None
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    cached = svc.get_cached('b1')
    assert cached and cached['summary'] == '요약 결과입니다.'


def test_dedupe_collapses_with_count():
    chats = [(f'u{i}', '억까 그만해요 진짜', '분노/불만') for i in range(50)]
    chats += [(f'v{i}', f'개웃기네 상황{i}', '웃음/재미') for i in range(20)]
    svc, llm, _ = setup(chats=chats)
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    prompt = llm.calls[0]
    assert '억까 그만해요 진짜 (×50)' in prompt, '완전일치 50건이 1건+횟수로 접혀야 함'
    assert prompt.count('억까 그만해요 진짜') == 1


def test_chants_not_sent_as_lines():
    """챈트 2,000건 + 내용 10건 → 프롬프트엔 내용 채팅 + 챈트 요약 한 줄만."""
    chats = [(f'u{i}', 'ㅋㅋㅋㅋㅋ', '웃음/재미') for i in range(2000)]
    chats += [(f'c{i}', f'개웃기네 상황{i}', '웃음/재미') for i in range(10)]
    svc, llm, _ = setup(chats=chats)
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    prompt = llm.calls[0]
    assert 'ㅋㅋㅋ(×2000)' in prompt, '대표 반응 줄에 규모 표기'
    assert '- ㅋㅋㅋ' not in prompt, '챈트가 채팅 목록에 들어가면 안 됨'
    assert '개웃기네 상황3' in prompt


def test_threshold_sampling_excludes_below():
    """분노 60% + 환호 30% + 혼란 8%(임계 미달) + 중립 소량 구성."""
    chats = [(f'a{i}', f'서버 왜 이래요 화나네 {i}', '분노/불만') for i in range(60)]
    chats += [(f'b{i}', f'그래도 플레이 미쳤다 {i}', '환호/감탄') for i in range(30)]
    chats += [(f'c{i}', f'이거 무슨 노래예요 {i}', '혼란/질문') for i in range(8)]
    chats += [(f'n{i}', f'지금 보스전 {i}페이즈', '중립') for i in range(2)]
    svc, llm, _ = setup(chats=chats)
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    prompt = llm.calls[0]
    assert '서버 왜 이래요' in prompt and '플레이 미쳤다' in prompt
    assert '무슨 노래예요' not in prompt, '임계(10%) 미달 라벨은 샘플 제외'
    assert '보스전' in prompt, '중립 맥락 쿼터는 포함'


def test_neutral_dominant_but_not_focus():
    """중립 90%여도 임계 라벨은 감정에서만 — 폴백으로 감정 상위 2개."""
    chats = [(f'n{i}', f'지금 {i}스테이지 진행중', '중립') for i in range(90)]
    chats += [(f'a{i}', f'개웃기네 {i}', '웃음/재미') for i in range(5)]
    chats += [(f'b{i}', f'왜 렉걸려요 {i}', '혼란/질문') for i in range(4)]
    svc, llm, _ = setup(chats=chats)
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    prompt = llm.calls[0]
    focus_section = prompt.split('서술 대상 감정')[1][:80]
    assert '웃음/재미' in focus_section, '폴백: 감정 상위가 서술 대상'
    assert '중립' not in focus_section.split('\n')[1], '중립은 서술 대상에 못 들어감'
    assert '개웃기네' in prompt


def test_sample_cap_300():
    chats = [(f'u{i}', f'전부 다른 내용의 채팅 {i}번', '웃음/재미') for i in range(1000)]
    svc, llm, _ = setup(chats=chats)
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    n_lines = llm.calls[0].count('\n- ')
    assert n_lines <= 300, f'채팅 줄 {n_lines}개 — 300 상한 초과'


def test_env_notice_appended_for_complaints():
    """환경 문제 5건+ → 코드가 카테고리 템플릿 문장을 요약 뒤에 붙임. LLM 무관."""
    chats = fill(n_laugh=40)
    chats += [(f'e{i}', f'화면 자꾸 끊겨요 {i}', '분노/불만') for i in range(6)]
    svc, llm, _ = setup(chats=chats)
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    assert '화면이 끊기거나 멈춘다는 채팅' in res['summary'], '화면 카테고리 템플릿'
    assert res['summary'].startswith('요약 결과입니다.'), 'LLM 브리핑 뒤에 이어붙는 구조'
    assert '환경' not in llm.calls[0], '프롬프트에는 환경 관련 내용이 없어야 함'


def test_env_notice_category_matches_issue():
    """소리 문제면 소리 템플릿 — nano의 예시문 복사 사고 재발 방지."""
    chats = fill(n_laugh=40)
    chats += [(f'e{i}', f'마이크 소리 작아요 {i}', '혼란/질문') for i in range(6)]
    svc, _, _ = setup(chats=chats)
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    assert '소리 관련 채팅' in res['summary']
    assert '화면' not in res['summary']


def test_env_notice_ignores_praise():
    """'화면 예쁘다'(긍정 라벨) 도배는 문제가 아니다 — 칭찬 오인 버그 재발 방지."""
    chats = fill(n_laugh=40)
    chats += [(f'p{i}', f'화면 미쳤다 예쁘다 {i}', '환호/감탄') for i in range(20)]
    svc, _, _ = setup(chats=chats)
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    assert res['summary'] == '요약 결과입니다.', f'안내가 붙으면 안 됨: {res["summary"]}'


def test_env_notice_below_threshold():
    """환경 문제가 4건(기준 5 미만)이면 안내 없음."""
    chats = fill(n_laugh=40)
    chats += [(f'e{i}', f'소리 작아요 {i}', '분노/불만') for i in range(4)]
    svc, _, _ = setup(chats=chats)
    res = svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    assert res['summary'] == '요약 결과입니다.'


def test_broadcast_isolation():
    svc, llm, r = setup(chats=fill(n_laugh=40), bid='b1')
    items = [(f'x{i}', f'다른 방송 채팅 {i}', '환호/감탄', P['환호/감탄']) for i in range(40)]
    BroadcastWindow(r, 'b2').add_batch(300, items, T0)
    svc.generate('b1', 'AUTO', now_ms=T0 + 1000)
    r.set('chat:b1:lock', '1')              # b1이 잠겨도 b2는 무관해야 함
    res = svc.generate('b2', 'AUTO', now_ms=T0 + 1000)
    assert '다른 방송 채팅' in llm.calls[-1]
    assert res['dominant'] == '환호/감탄'


if __name__ == '__main__':
    fns = [v for k, v in sorted(globals().items()) if k.startswith('test_')]
    for fn in fns:
        fn()
        print(f'  OK  {fn.__name__}')
    print(f'\n{len(fns)}건 통과')

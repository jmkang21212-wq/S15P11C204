"""반복 질문 집계 단위 테스트 — 모델·GMS 없이 실행 가능 (fakeredis + 가짜 임베딩).

    python tests/test_questions.py     또는     python -m pytest tests/ -q
"""
import os
import sys

import fakeredis
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.questions import QuestionTracker, content_len, is_askable

T0 = 1_700_000_000_000

# 가짜 임베딩: 텍스트의 첫 글자로 방향을 정한다. 같은 첫 글자면 유사도 1.0,
# 다르면 0.0 — 임계값(0.38)을 기준으로 결정적으로 갈린다.
_AXIS = {}


def fake_embed(texts):
    calls['n'] += 1
    calls['texts'].extend(texts)
    out = []
    for t in texts:
        key = t[0] if t else '_'
        if key not in _AXIS:
            v = np.zeros(8, dtype=np.float32)
            v[len(_AXIS) % 8] = 1.0
            _AXIS[key] = v
        out.append(_AXIS[key])
    return np.array(out)


calls = {'n': 0, 'texts': []}


def make():
    calls['n'] = 0
    calls['texts'] = []
    _AXIS.clear()
    return QuestionTracker(fakeredis.FakeRedis(), fake_embed)


def feed(tr, bid, pairs, ms=T0):
    """[(uid, text)] 를 대기열에 넣고 즉시 처리."""
    for i, (uid, text) in enumerate(pairs):
        tr.offer(bid, uid, text, ms + i)
    return tr.flush(bid)


# ---------------------------------------------------------------- 필터

def test_reaction_rejected():
    """빈도만 세면 상위가 전부 리액션이 된다 — 실측 근거는 questions.py 참조."""
    for t in ['ㅋㅋㅋ', '???', 'ㅇㄴ?', 'ㄱㄴ?', 'ㄷㄷ']:
        assert not is_askable(t), t


def test_short_content_rejected():
    """완성형 한글이라 리액션 필터를 통과하는 것들 — 길이로 걸러야 한다."""
    for t in ['난가?', '어라?', '실수?', '그래?']:
        assert not is_askable(t), t


def test_real_questions_pass():
    for t in ['오늘 몇 시까지 하세요?', '마우스 뭐 쓰세요?', '그 챔피언 왜 삼?']:
        assert is_askable(t), t


def test_content_len_ignores_reaction_chars():
    assert content_len('난가?') == 2
    assert content_len('오늘 몇 시까지 하세요?') == 9


def test_offer_skips_filtered():
    tr = make()
    assert tr.offer('b', 'u1', '오늘 몇 시까지 하세요?') is True
    assert tr.offer('b', 'u2', '???') is False
    assert tr.r.llen('chat:b:q:pending') == 1


# ---------------------------------------------------------------- 클러스터링

def test_similar_merge_distinct_split():
    tr = make()
    feed(tr, 'b', [('u1', '언제 끝나나요?'), ('u2', '언제 하시나요?'),
                   ('u3', '마우스 뭐 쓰세요?')])
    res = tr.top('b', limit=10, exclude=())
    assert res['totalQuestions'] == 2, res       # 언제* 2건이 하나로, 마우스가 따로


def test_exact_cache_skips_embedding():
    """같은 문장 재등장 → 임베딩 호출 없이 카운트만."""
    tr = make()
    feed(tr, 'b', [('u1', '오늘 몇 시까지 하세요?')])
    first = calls['n']
    feed(tr, 'b', [('u2', '오늘 몇 시까지 하세요?')])
    assert calls['n'] == first, '완전일치인데 임베딩을 다시 호출했다'


def test_spam_counts_one_asker():
    """한 사람이 10번 물어도 askers는 1 — '다들 궁금해한다'를 나타내야 한다."""
    tr = make()
    feed(tr, 'b', [('spammer', '오늘 몇 시까지 하세요?')] * 10)
    res = tr.top('b', limit=5, exclude=())
    tr.r.zadd('chat:b:q:rank', {'1': 1})          # Q_MIN_ASKERS 우회용 확인
    q = tr.top('b', limit=5, exclude=())['questions']
    assert res['totalQuestions'] == 1
    assert tr.r.scard('chat:b:q:a:1') == 1
    assert int(tr.r.hget('chat:b:q:msgs', 1)) == 10


def test_min_askers_hides_rare():
    tr = make()
    feed(tr, 'b', [('u1', '오늘 몇 시까지 하세요?'), ('u2', '오늘 몇 시까지 하세요?')])
    assert tr.top('b', limit=5, exclude=())['questions'] == []   # 2명 < Q_MIN_ASKERS(3)
    feed(tr, 'b', [('u3', '오늘 몇 시까지 하세요?')])
    assert len(tr.top('b', limit=5, exclude=())['questions']) == 1


def test_representative_is_most_common_phrasing():
    tr = make()
    feed(tr, 'b', [('u1', '언제 끝나요?'), ('u2', '언제 끝나요?'),
                   ('u3', '언제 끝나요?'), ('u4', '언제 하시나요?')])
    q = tr.top('b', limit=5, exclude=())['questions'][0]
    assert q['text'] == '언제 끝나요?', q


# ---------------------------------------------------------------- questionId

def test_qid_stable_across_changes():
    """Spring이 'N번 지웠음'을 저장하므로 ID가 흔들리면 안 된다."""
    tr = make()
    feed(tr, 'b', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    qid = tr.top('b', limit=5, exclude=())['questions'][0]['questionId']
    feed(tr, 'b', [(f'v{i}', '마우스 뭐 쓰세요?') for i in range(3)])
    feed(tr, 'b', [(f'w{i}', '내일도 방송해요?') for i in range(3)])
    after = {q['text']: q['questionId'] for q in tr.top('b', limit=9, exclude=())['questions']}
    assert after['오늘 몇 시까지 하세요?'] == qid


def test_qid_never_reused():
    tr = make()
    feed(tr, 'b', [('u1', '오늘 몇 시까지 하세요?')])
    tr.r.hdel('chat:b:q:text', 1)                 # 클러스터 제거 흉내
    feed(tr, 'b', [('u2', '마우스 뭐 쓰세요?')])
    assert int(tr.r.get('chat:b:q:seq')) == 2, '제거된 id를 재사용했다'


# ---------------------------------------------------------------- exclude

def test_exclude_promotes_next():
    """지운 자리를 다음 순위가 채워야 한다 (계약 4장)."""
    tr = make()
    for n, text in [(6, '언제 끝나요?'), (5, '마우스 뭐 쓰세요?'),
                    (4, '내일도 방송해요?'), (3, '합방 언제 해요?')]:
        feed(tr, 'b', [(f'{text[:2]}{i}', text) for i in range(n)])

    top2 = tr.top('b', limit=2, exclude=())['questions']
    assert len(top2) == 2
    dropped = [q['questionId'] for q in top2]

    after = tr.top('b', limit=2, exclude=dropped)['questions']
    assert len(after) == 2, '제외 후에도 limit개가 채워져야 한다'
    assert all(q['questionId'] not in dropped for q in after)


def test_exclude_unknown_id_ignored():
    tr = make()
    feed(tr, 'b', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    assert len(tr.top('b', limit=5, exclude=(99999,))['questions']) == 1


# ---------------------------------------------------------------- 견고성

def test_centroid_rebuild_after_cache_loss():
    """워커 교체·재시작 → 중심점을 대표 문장 재임베딩으로 복원한다."""
    tr = make()
    feed(tr, 'b', [(f'u{i}', '언제 끝나요?') for i in range(3)])
    tr._centroids.clear()                          # 프로세스 캐시 유실 상황
    feed(tr, 'b', [('u9', '언제 하시나요?')])       # 같은 계열
    assert tr.top('b', limit=5, exclude=())['totalQuestions'] == 1, '복원 후 새 클러스터가 생겼다'


def test_pending_capped():
    tr = make()
    for i in range(20):
        tr.offer('b', f'u{i}', f'질문 테스트 {i}번입니다?')
    assert tr.r.llen('chat:b:q:pending') == 20     # 상한(5000) 아래라 그대로


def test_embed_failure_keeps_worker_alive():
    tr = QuestionTracker(fakeredis.FakeRedis(), lambda t: (_ for _ in ()).throw(RuntimeError('gms down')))
    tr.offer('b', 'u1', '오늘 몇 시까지 하세요?')
    try:
        tr.flush('b')
    except RuntimeError:
        pass                                       # flush는 올려도 워커 루프가 잡는다
    assert tr.r.exists('chat:b:q:lock') == 0, '예외 시 락이 안 풀렸다'


def test_disabled_without_embed_fn():
    tr = QuestionTracker(fakeredis.FakeRedis(), None)
    assert tr.offer('b', 'u1', '오늘 몇 시까지 하세요?') is False
    assert tr.top('b', limit=5, exclude=())['questions'] == []


def test_broadcast_isolation():
    tr = make()
    feed(tr, 'a', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    assert tr.top('b', limit=5, exclude=())['totalQuestions'] == 0


# ---------------------------------------------------------------- FAQ 판정

def _judge_stub(reply):
    """LLM 대신 고정 응답. 호출 횟수를 센다."""
    state = {'n': 0, 'prompts': []}

    def fn(prompt):
        state['n'] += 1
        state['prompts'].append(prompt)
        return reply
    fn.state = state
    return fn


def test_faq_tag_attached():
    llm = _judge_stub('1 FAQ\n2 CONTEXT')
    tr = QuestionTracker(fakeredis.FakeRedis(), fake_embed, llm)
    _AXIS.clear()
    feed(tr, 'b', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    feed(tr, 'b', [(f'v{i}', '무슨 실수?를 말하는거야?') for i in range(3)])
    tags = {q['text']: q['type'] for q in tr.top('b', limit=5, exclude=())['questions']}
    assert set(tags.values()) <= {'FAQ', 'CONTEXT'}, tags
    assert llm.state['n'] >= 1


def test_faq_cached_not_rejudged():
    llm = _judge_stub('1 FAQ')
    tr = QuestionTracker(fakeredis.FakeRedis(), fake_embed, llm)
    _AXIS.clear()
    feed(tr, 'b', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    first = llm.state['n']
    feed(tr, 'b', [('u9', '오늘 몇 시까지 하세요?')])
    assert llm.state['n'] == first, '이미 판정한 클러스터를 다시 물었다'


def test_faq_skipped_below_min_askers():
    """노출도 안 될 클러스터에 LLM을 쓰지 않는다 (비용)."""
    llm = _judge_stub('1 FAQ')
    tr = QuestionTracker(fakeredis.FakeRedis(), fake_embed, llm)
    _AXIS.clear()
    feed(tr, 'b', [('u1', '오늘 몇 시까지 하세요?')])
    assert llm.state['n'] == 0


def test_faq_none_without_llm():
    """llm_fn 없어도 목록은 그대로 나오고 type만 None."""
    tr = make()
    feed(tr, 'b', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    q = tr.top('b', limit=5, exclude=())['questions'][0]
    assert q['type'] is None


def test_faq_llm_failure_is_survivable():
    def boom(prompt):
        raise RuntimeError('gms down')
    tr = QuestionTracker(fakeredis.FakeRedis(), fake_embed, boom)
    _AXIS.clear()
    feed(tr, 'b', [(f'u{i}', '오늘 몇 시까지 하세요?') for i in range(3)])
    q = tr.top('b', limit=5, exclude=())['questions']
    assert len(q) == 1 and q[0]['type'] is None, '판정 실패가 목록을 막으면 안 된다'


if __name__ == '__main__':
    fails = 0
    for name, fn in sorted(globals().items()):
        if name.startswith('test_') and callable(fn):
            try:
                fn()
                print(f'  OK  {name}')
            except Exception as e:
                fails += 1
                print(f'  FAIL {name}: {type(e).__name__}: {e}')
    print(f'\n{"통과" if not fails else f"{fails}건 실패"}')
    sys.exit(1 if fails else 0)

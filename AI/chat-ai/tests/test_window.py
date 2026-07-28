"""슬라이딩 윈도우 단위 테스트 — 모델·Redis 없이 실행 가능 (fakeredis).

    python tests/test_window.py     또는     python -m pytest tests/ -q
"""
import os
import sys

import fakeredis

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.labels import LABELS
from chatai.window import BroadcastWindow

T0 = 1_700_000_000_000          # 기준 시각(ms) — 시계를 주입해 결정적으로 테스트

# LABELS 순서: 웃음0 환호1 응원2 놀람3 슬픔4 분노5 혼란6 중립7
P_LAUGH = [1.0, 0, 0, 0, 0, 0, 0, 0]
P_NEUT = [0, 0, 0, 0, 0, 0, 0, 1.0]
P_HALF = [0.5, 0, 0, 0, 0, 0, 0, 0.5]


def make():
    return BroadcastWindow(fakeredis.FakeRedis(), 'test')


def test_unique_same_uid_counts_once():
    w = make()
    m = w.add_batch(100, [('u1', P_LAUGH), ('u1', P_NEUT), ('u1', P_LAUGH)], T0)
    assert m['uniqueChatters'] == 1, m
    assert m['chatCount'] == 3


def test_unique_survives_partial_expiry():
    """u1의 옛 채팅이 윈도우를 벗어나도 최근 채팅이 있으면 유니크 유지."""
    w = make()
    w.add_batch(100, [('u1', P_LAUGH)], T0)
    m = w.add_batch(100, [('u1', P_NEUT)], T0 + 170_000)   # 첫 채팅은 3분 직전
    assert m['uniqueChatters'] == 1
    m = w.add_batch(100, [], T0 + 200_000)                 # 첫 채팅은 만료, 둘째는 생존
    assert m['uniqueChatters'] == 1, 'ZADD가 score를 갱신하므로 유지되어야 함'
    m = w.add_batch(100, [], T0 + 170_000 + 181_000)       # 둘째도 만료
    assert m['uniqueChatters'] == 0


def test_engagement_null_when_no_viewers():
    w = make()
    m = w.add_batch(None, [('u1', P_LAUGH)], T0)
    assert m['engagement'] is None and m['viewers'] is None
    m = w.add_batch(0, [('u2', P_LAUGH)], T0 + 1000)
    assert m['engagement'] is None, '0으로 나누기 금지'


def test_viewers_kept_when_none():
    """viewers=None(폴링 실패)이면 마지막 정상값 유지."""
    w = make()
    w.add_batch(500, [('u1', P_LAUGH)], T0)
    m = w.add_batch(None, [('u2', P_LAUGH)], T0 + 1000)
    assert m['viewers'] == 500
    assert m['engagement'] == round(2 / 500, 4)


def test_empty_batch_slides_window():
    w = make()
    w.add_batch(100, [('u1', P_LAUGH), ('u2', P_LAUGH)], T0)
    m = w.add_batch(100, [], T0 + 181_000)      # 3분 경과, 빈 배치
    assert m['uniqueChatters'] == 0, '빈 배치도 윈도우를 미끄러뜨려야 함'


def test_anonymous_uid_excluded_from_unique():
    w = make()
    m = w.add_batch(100, [('', P_LAUGH), ('u1', P_NEUT)], T0)
    assert m['uniqueChatters'] == 1, 'uid 없는 메시지는 유니크 제외'
    assert m['chatCount'] == 2, '분포에는 포함'


def test_distribution_sums_to_one():
    w = make()
    m = w.add_batch(100, [('u1', P_LAUGH), ('u2', P_NEUT), ('u3', P_HALF)], T0)
    assert abs(sum(m['distribution'].values()) - 1.0) < 0.01
    assert m['distribution']['웃음/재미'] == 0.5      # (1 + 0 + 0.5) / 3
    assert m['dominant'] in ('웃음/재미', '중립')      # 동률 0.5


def test_distribution_expires_with_buckets():
    """버킷이 윈도우 밖으로 나가면 분포에서 빠진다 (읽기 범위 기반)."""
    w = make()
    w.add_batch(100, [('u1', P_LAUGH)], T0)
    m = w.add_batch(100, [('u2', P_NEUT)], T0 + 179_000)
    assert m['chatCount'] == 2
    m = w.metrics(T0 + 181_000)                 # 첫 버킷이 180초 밖
    assert m['chatCount'] == 1
    assert m['distribution']['중립'] == 1.0


def test_empty_window_metrics():
    w = make()
    m = w.metrics(T0)
    assert m['chatCount'] == 0 and m['uniqueChatters'] == 0
    assert m['dominant'] is None
    assert abs(sum(m['distribution'].values())) < 1e-9


def test_delete_idempotent():
    w = make()
    w.add_batch(100, [('u1', P_LAUGH)], T0)
    assert w.delete() > 0
    assert w.delete() == 0, '두 번째 삭제도 조용히 성공해야 함'
    m = w.metrics(T0 + 1000)
    assert m['chatCount'] == 0 and m['uniqueChatters'] == 0


def test_results_independent_broadcasts():
    r = fakeredis.FakeRedis()
    a = BroadcastWindow(r, 'a')
    b = BroadcastWindow(r, 'b')
    a.add_batch(100, [('u1', P_LAUGH)], T0)
    m = b.metrics(T0)
    assert m['chatCount'] == 0, '방송끼리 키가 섞이면 안 됨'


if __name__ == '__main__':
    fns = [v for k, v in sorted(globals().items()) if k.startswith('test_')]
    for fn in fns:
        fn()
        print(f'  OK  {fn.__name__}')
    print(f'\n{len(fns)}건 통과')

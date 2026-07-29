"""수집 워커의 판단 로직 검증.

스레드를 띄우지 않고 메서드를 직접 부른다. 여기서 잡고 싶은 것은 동시성이
아니라 "시각을 어디서 가져오는가"와 "공백을 언제 열고 닫는가"다.
"""
import queue
import time

import pytest

from chatcollector.session import (
    GAP_AUTH_FAILED,
    RECONNECTING,
    RUNNING,
    CollectorSession,
)


class FakeSpring:
    def __init__(self):
        self.gaps = []
        self.statuses = []
        self.chat_batches = []

    def report_gap(self, session_id, started_at, ended_at, reason):
        self.gaps.append((session_id, started_at, ended_at, reason))

    def report_status(self, session_id, status, occurred_at):
        self.statuses.append((session_id, status, occurred_at))

    def send_chat_batch(self, session_id, items):
        self.chat_batches.append(items)
        return {'acceptedCount': len(items), 'duplicateCount': 0}


@pytest.fixture
def session():
    return CollectorSession(9002, FakeSpring())


# --- sent_at ---


def test_messageTime을_sent_at으로_쓴다(session):
    received_at = 1_784_000_000.0
    message_time_ms = int((received_at - 2) * 1000)

    assert session._sent_at(message_time_ms, received_at) == pytest.approx(received_at - 2)
    assert session.clock_skew_corrected == 0


def test_시계가_크게_어긋나면_수신_시각으로_대체한다(session):
    # messageTime은 클라이언트 시계다(HIGHLIGHT_SPEC 가드 8). 그대로 두면
    # chat_messages의 먼 미래 청크에 행이 생긴다.
    received_at = 1_784_000_000.0
    a_year_later_ms = int((received_at + 365 * 24 * 3600) * 1000)

    assert session._sent_at(a_year_later_ms, received_at) == received_at
    assert session.clock_skew_corrected == 1


def test_eventSentAt의_9시간_오차는_대체된다(session):
    # 타임존 없는 KST 로컬 문자열을 UTC로 잘못 파싱하면 딱 9시간 어긋난다.
    # 그런 값이 messageTime 자리에 들어와도 통과시키지 않는다.
    received_at = 1_784_000_000.0
    skewed_ms = int((received_at + 9 * 3600) * 1000)

    assert session._sent_at(skewed_ms, received_at) == received_at
    assert session.clock_skew_corrected == 1


# --- 채팅 변환 ---


def test_원본_식별자_대신_해시를_큐에_넣는다(session):
    session._on_chat({
        'senderChannelId': 'raw-channel-id',
        'content': 'ㅋㅋㅋ',
        'messageTime': int(time.time() * 1000),
    })

    item = session._chat.get_nowait()
    assert item['viewerHash'].startswith('sha256:')
    assert 'raw-channel-id' not in item['viewerHash']
    assert item['message'] == 'ㅋㅋㅋ'
    assert set(item) == {'viewerHash', 'message', 'sentAt'}


def test_스트리머_채팅을_따로_센다(session):
    now_ms = int(time.time() * 1000)
    session._on_chat({
        'senderChannelId': 'a', 'content': '안녕', 'messageTime': now_ms,
        'profile': {'userRoleCode': 'streamer'},
    })
    session._on_chat({
        'senderChannelId': 'b', 'content': '안녕', 'messageTime': now_ms,
    })

    assert session.received == 2
    assert session.streamer_messages == 1


def test_큐가_차면_오래된_것부터_버린다(session):
    session._chat = queue.Queue(maxsize=2)

    session._offer({'n': 1})
    session._offer({'n': 2})
    session._offer({'n': 3})

    assert session._chat.get_nowait() == {'n': 2}
    assert session._chat.get_nowait() == {'n': 3}
    assert session.dropped == 1


# --- 공백 구간 ---


def test_끊기면_공백을_열고_보고한다(session):
    session._open_gap(GAP_AUTH_FAILED)

    assert session._state == RECONNECTING
    assert len(session._spring.gaps) == 1
    _, started_at, ended_at, reason = session._spring.gaps[0]
    assert started_at is not None
    # 열 때는 끝을 모른다. 수집기가 이 구간 도중에 죽어도 "여기서부터 못 봤다"가 남는다.
    assert ended_at is None
    assert reason == GAP_AUTH_FAILED


def test_이미_열린_공백을_두_번_열지_않는다(session):
    session._open_gap(GAP_AUTH_FAILED)
    session._open_gap(GAP_AUTH_FAILED)

    assert len(session._spring.gaps) == 1


def test_복구하면_같은_시작_시각으로_공백을_닫는다(session):
    session._open_gap(GAP_AUTH_FAILED)
    opened_at = session._spring.gaps[0][1]

    session._close_gap()

    assert session._state == RUNNING
    assert len(session._spring.gaps) == 2
    _, started_at, ended_at, reason = session._spring.gaps[1]
    # started_at이 같아야 Spring의 (session_id, started_at) Upsert가 행을 닫는다.
    assert started_at == opened_at
    assert ended_at is not None
    # 연 이유를 그대로 유지한다.
    assert reason == GAP_AUTH_FAILED


def test_열린_공백이_없으면_닫아도_보고하지_않는다(session):
    # 첫 핸드셰이크가 바로 성공한 경우다. 유실 구간이 없으니 행을 만들면 안 된다.
    session._close_gap()

    assert session._spring.gaps == []
    assert session._state == RUNNING


# --- 상태 스냅샷 ---


def test_시청자_수가_없으면_stale이다(session):
    # HIGHLIGHT_SPEC 가드 4: 마지막 정상값이 없으면 판정을 생략해야 한다.
    snapshot = session.snapshot()

    assert snapshot['lastViewerCount'] is None
    assert snapshot['viewerStale'] is True


def test_방금_받은_시청자_수는_stale이_아니다(session):
    session._last_viewer_count = 2417
    session._last_viewer_at = time.time()

    snapshot = session.snapshot()

    assert snapshot['lastViewerCount'] == 2417
    assert snapshot['viewerStale'] is False


def test_5분_넘은_시청자_수는_stale이다(session):
    session._last_viewer_count = 2417
    session._last_viewer_at = time.time() - 301

    assert session.snapshot()['viewerStale'] is True

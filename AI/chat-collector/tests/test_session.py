"""수집 워커의 판단 로직 검증.

스레드를 띄우지 않고 메서드를 직접 부른다. 여기서 잡고 싶은 것은 동시성이
아니라 "시각을 어디서 가져오는가"와 "공백을 언제 열고 닫는가"다.
"""
import queue
import threading
import time

import pytest
from engineio import packet as eio_packet

from chatcollector.session import (
    GAP_AUTH_FAILED,
    RECONNECTING,
    RUNNING,
    STOPPED,
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
    # 정확 일치 단정은 의도된 감지선이다 — 필드를 늘릴 때 이 목록도 함께 늘려서
    # "원본 식별자가 새 필드로 새어 나가지 않는지"를 강제로 재검토하게 만든다.
    assert set(item) == {'viewerHash', 'message', 'sentAt', 'nickname', 'userRole'}


def test_닉네임과_역할은_원문으로_실린다(session):
    session._on_chat({
        'senderChannelId': 'raw-channel-id',
        'content': '오늘도 왔어요',
        'messageTime': int(time.time() * 1000),
        'profile': {'nickname': '한별팬1호', 'userRoleCode': 'common_user'},
    })

    item = session._chat.get_nowait()
    assert item['nickname'] == '한별팬1호'
    assert item['userRole'] == 'common_user'


def test_profile이_없으면_닉네임은_None이다(session):
    session._on_chat({
        'senderChannelId': 'raw-channel-id',
        'content': '첫 채팅',
        'messageTime': int(time.time() * 1000),
    })

    item = session._chat.get_nowait()
    assert item['nickname'] is None
    assert item['userRole'] is None


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


# --- 상태 보고 ---


def test_시작_직후_보고는_STOPPED가_아니다(session):
    # 하트비트의 첫 보고는 스레드를 띄운 직후 나가고 RUNNING은 구독을 마친
    # 뒤에야 올라간다. 그 사이에 STOPPED가 나가면 Spring이 endPlatform으로
    # 방송 세션을 닫아버린다(실측: 시작 0.64초 뒤 ENDED).
    session._report_status_safely()

    assert [status for _, status, _ in session._spring.statuses] == [RECONNECTING]


def test_구독하면_하트비트를_기다리지_않고_RUNNING을_보고한다(session):
    # 이 보고가 없으면 정상 방송이 최대 HEARTBEAT_SEC 동안 "재연결 중"으로 보인다.
    session._close_gap()

    assert [status for _, status, _ in session._spring.statuses] == [RUNNING]


def test_STOPPED는_stop만_보고한다(session):
    # Spring은 STOPPED 보고를 세션 종료 지시로 받는다. 다른 경로에서 새어 나가면
    # 방송 중인 세션이 닫힌다.
    session._close_gap()
    session._open_gap(GAP_AUTH_FAILED)
    session._close_gap()
    assert STOPPED not in [status for _, status, _ in session._spring.statuses]

    session.stop()

    assert [status for _, status, _ in session._spring.statuses][-1] == STOPPED


# --- 상태 스냅샷 ---


def test_시청자_수가_없으면_stale이다(session):
    # HIGHLIGHT_SPEC 가드 4: 마지막 정상값이 없으면 판정을 생략해야 한다.
    snapshot = session.snapshot()

    assert snapshot['lastViewerCount'] is None
    assert snapshot['viewerStale'] is True
    assert snapshot['viewerUsable'] is False


def test_방금_받은_시청자_수는_stale이_아니다(session):
    session._last_viewer_count = 2417
    session._last_viewer_at = time.time()

    snapshot = session.snapshot()

    assert snapshot['lastViewerCount'] == 2417
    assert snapshot['viewerStale'] is False
    assert snapshot['viewerUsable'] is True


def test_5분_넘은_시청자_수는_stale이다(session):
    session._last_viewer_count = 2417
    session._last_viewer_at = time.time() - 301

    snapshot = session.snapshot()

    assert snapshot['viewerStale'] is True
    assert snapshot['viewerUsable'] is False


# --- 규격 위반 패킷 ---


def test_치지직의_규격_위반_패킷에_예외가_새지_않는다(session):
    # 2026-07-29 실측. 치지직은 우리 CONNECT `0{}`의 `{}`를 namespace로 잘못 읽고
    # `4{},"Invalid namespace"`를 되돌려준다. JSON이 두 개 붙어 있어
    # python-socketio의 json.loads가 "Extra data"로 깨지고, 그게 배경 스레드라
    # 연결마다 처리되지 않은 스택트레이스가 찍혔다.
    client = session._make_socket_client()

    client._handle_eio_message('4{},"Invalid namespace"')

    assert session.malformed_packets == 1
    assert session.snapshot()['malformedPackets'] == 1


def test_정상_패킷은_그대로_처리된다(session):
    client = session._make_socket_client()
    delivered = []
    client.on('CHAT', lambda payload: delivered.append(payload))

    client._handle_eio_message('2["CHAT","{}"]')

    assert delivered == ['{}']
    assert session.malformed_packets == 0


def test_핸들러_안의_ValueError는_삼키지_않는다(session):
    # 규격 위반 패킷을 버리려고 super()를 try로 감싸면 우리 버그까지 숨는다.
    client = session._make_socket_client()

    @client.on('CHAT')
    def _boom(payload):  # noqa: ANN001
        raise ValueError('우리 코드의 버그')

    with pytest.raises(ValueError, match='우리 코드의 버그'):
        client._handle_eio_message('2["CHAT","{}"]')

    assert session.malformed_packets == 0


def test_socket_ping을_주기적으로_보낸다(session, monkeypatch):
    # 치지직이 ping을 보내지 않아 우리가 보낸다. 안 보내면 85초 안에 끊긴다.
    monkeypatch.setattr('chatcollector.session.SOCKET_PING_SEC', 0.01)
    sent = []

    class FakeEngine:
        def _send_packet(self, packet):
            sent.append(packet.packet_type)

    class FakeClient:
        eio = FakeEngine()

    stop = threading.Event()
    thread = threading.Thread(
        target=session._socket_ping_loop, args=(FakeClient(), stop), daemon=True)
    thread.start()
    time.sleep(0.1)
    stop.set()
    thread.join(timeout=1)

    assert len(sent) >= 2
    # engine.io PING은 패킷 타입 2다.
    assert set(sent) == {eio_packet.PING}


def test_socket_ping은_송신_실패하면_멈춘다(session, monkeypatch):
    # 이미 끊긴 연결에 계속 쓰려 하면 안 된다. 복구는 재핸드셰이크 담당이다.
    monkeypatch.setattr('chatcollector.session.SOCKET_PING_SEC', 0.01)
    attempts = []

    class DeadEngine:
        def _send_packet(self, packet):
            attempts.append(1)
            raise RuntimeError('not connected')

    class DeadClient:
        eio = DeadEngine()

    stop = threading.Event()
    thread = threading.Thread(
        target=session._socket_ping_loop, args=(DeadClient(), stop), daemon=True)
    thread.start()
    thread.join(timeout=1)

    assert not thread.is_alive()
    assert len(attempts) == 1


def test_방금_측정한_시청자_0명은_판정에_쓸_수_없다(session):
    # 실측: 방송을 켜고 혼자 보면 concurrentUserCount가 0으로 온다.
    # 폴링은 성공했으니 stale이 아니지만, engagement = uniq / viewers의
    # 분모로 쓰면 0으로 나눈다(가드 4).
    session._last_viewer_count = 0
    session._last_viewer_at = time.time()

    snapshot = session.snapshot()

    assert snapshot['viewerStale'] is False
    assert snapshot['viewerUsable'] is False

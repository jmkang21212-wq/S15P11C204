from datetime import datetime, timezone

import httpx

from chatcollector.config import HEARTBEAT_SEC
from chatcollector.youtube import YouTubeCollectorSession


class FakeSpring:
    def __init__(self):
        self.spec = {
            'accessToken': 'token-1',
            'liveChatId': 'chat-1',
            'externalBroadcastId': 'video-1',
        }
        self.refreshed = False

    def refresh_spec(self):
        self.refreshed = True
        self.spec = {**self.spec, 'accessToken': 'token-2'}
        return self.spec


def make_session():
    return YouTubeCollectorSession(71, FakeSpring())


def response(status: int, payload=None, headers=None):
    request = httpx.Request('GET', 'https://www.googleapis.com/youtube/v3/liveChat/messages')
    return httpx.Response(status, request=request, json=payload or {}, headers=headers)


def test_normalizes_provider_events_and_micros():
    session = make_session()
    rows = session._normalize_messages([{
        'id': 'message-1',
        'snippet': {
            'type': 'superChatEvent',
            'displayMessage': '응원합니다',
            'publishedAt': datetime(2026, 8, 1, tzinfo=timezone.utc).isoformat(),
            'superChatDetails': {'amountMicros': '2500000', 'currency': 'KRW'},
        },
        'authorDetails': {'channelId': 'viewer-1'},
    }])

    assert len(rows) == 1
    assert rows[0]['platformMessageId'] == 'message-1'
    assert rows[0]['messageType'] == 'DONATION'
    assert rows[0]['amount'] == 2.5
    assert rows[0]['currency'] == 'KRW'


def test_only_terminal_live_chat_reasons_end_session():
    session = make_session()

    quota = response(403, {'error': {'errors': [{'reason': 'quotaExceeded'}]}})
    ended = response(403, {'error': {'errors': [{'reason': 'liveChatEnded'}]}})

    assert session._is_terminal_chat_error(quota) is False
    assert session._is_terminal_chat_error(ended) is True
    assert session._is_terminal_chat_error(response(404)) is True


def test_refreshes_expired_access_token_and_honors_retry_after():
    session = make_session()

    session._refresh_access_token()

    assert session._spring.refreshed is True
    assert session._client.headers['Authorization'] == 'Bearer token-2'
    assert session._retry_delay(response(429, headers={'Retry-After': '7'}), 1.0) == 7.0


class RecordingSpring(FakeSpring):
    def __init__(self):
        super().__init__()
        self.reports = []

    def report_status(self, session_id, state, occurred_at):
        self.reports.append((session_id, state))


class StopAfter:
    """지정한 횟수만 wait()가 False를 돌려주는 가짜 Event. 실제 대기 없이 루프를 돌린다."""

    def __init__(self, passes: int):
        self.remaining = passes
        self.timeouts = []

    def wait(self, timeout=None):
        self.timeouts.append(timeout)
        self.remaining -= 1
        return self.remaining < 0

    def set(self):
        self.remaining = -1

    def is_set(self):
        return self.remaining < 0


def test_heartbeat_loop_reports_repeatedly_until_stopped():
    """start()의 단발 보고만 있으면 Spring이 30초 후 STOPPED로 강등한다.

    채팅은 정상 수집되는데 콘솔의 수집기 표시만 꺼지는 증상이 실제로 났다.
    """
    session = YouTubeCollectorSession(71, RecordingSpring())
    session._state = 'RUNNING'
    session._stop = StopAfter(3)

    session._heartbeat_loop()

    assert session._spring.reports == [(71, 'RUNNING')] * 3
    # 보고 주기는 Spring의 heartbeat-timeout보다 짧은 설정값을 써야 한다.
    assert session._stop.timeouts == [HEARTBEAT_SEC] * 4


def test_start_spawns_heartbeat_thread():
    """루프가 있어도 start()에서 띄우지 않으면 같은 증상이 재발한다."""
    session = YouTubeCollectorSession(71, RecordingSpring())
    # 실제 폴링은 하지 않는다 — 여기서 검증할 것은 스레드 구성이다.
    session._chat_loop = lambda: None
    session._viewer_loop = lambda: None
    session._heartbeat_loop = lambda: None

    session.start()
    try:
        names = [thread.name for thread in session._threads]
    finally:
        session._stop.set()
        for thread in session._threads:
            thread.join(timeout=2)

    assert 'youtube-heartbeat-71' in names

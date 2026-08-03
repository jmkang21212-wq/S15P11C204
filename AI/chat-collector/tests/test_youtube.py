from datetime import datetime, timezone

import httpx

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

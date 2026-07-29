"""CHAT/SYSTEM 페이로드 해석 검증.

치지직이 같은 필드를 문자열로도 객체로도 보내서, 두 형태를 모두 받는지가
이 모듈의 핵심이다.
"""
import json

from chatcollector.chzzk import parse_chat_event, parse_system_event

_BASE = {
    'senderChannelId': 'abc123',
    'content': '이거 클립 박제각',
    'messageTime': 1_784_000_000_000,
}


def test_단일_객체_페이로드를_읽는다():
    events = parse_chat_event(dict(_BASE))

    assert len(events) == 1
    assert events[0].sender_channel_id == 'abc123'
    assert events[0].content == '이거 클립 박제각'
    assert events[0].message_time_ms == 1_784_000_000_000


def test_목록_페이로드를_읽는다():
    events = parse_chat_event([dict(_BASE), dict(_BASE, content='ㅋㅋㅋ')])

    assert [event.content for event in events] == ['이거 클립 박제각', 'ㅋㅋㅋ']


def test_JSON_문자열_페이로드를_읽는다():
    events = parse_chat_event(json.dumps([_BASE]))

    assert len(events) == 1
    assert events[0].sender_channel_id == 'abc123'


def test_profile이_JSON_문자열이어도_역할을_읽는다():
    payload = dict(_BASE, profile=json.dumps({'userRoleCode': 'streamer'}))

    assert parse_chat_event(payload)[0].user_role_code == 'streamer'


def test_profile이_객체여도_역할을_읽는다():
    payload = dict(_BASE, profile={'userRoleCode': 'manager'})

    assert parse_chat_event(payload)[0].user_role_code == 'manager'


def test_profile이_없으면_역할은_None이다():
    assert parse_chat_event(dict(_BASE))[0].user_role_code is None


def test_이모티콘_개수를_센다():
    payload = dict(_BASE, emojis={'emoji1': 'https://a', 'emoji2': 'https://b'})

    assert parse_chat_event(payload)[0].emoji_count == 2


def test_이모티콘이_없으면_0이다():
    assert parse_chat_event(dict(_BASE))[0].emoji_count == 0


def test_필수_필드가_빠진_항목은_버린다():
    payload = [dict(_BASE), {'senderChannelId': 'x'}]

    assert len(parse_chat_event(payload)) == 1


def test_messageTime이_숫자가_아니면_버린다():
    # 문자열로 오면 epoch ms로 쓸 수 없다. 조용히 0으로 떨어지면 안 된다.
    payload = dict(_BASE, messageTime='2026-07-28T11:43:32')

    assert parse_chat_event(payload) == []


def test_빈_내용의_채팅도_받는다():
    # 이모티콘만 있는 채팅은 content가 빈 문자열이다. 버리면 안 된다.
    events = parse_chat_event(dict(_BASE, content=''))

    assert len(events) == 1
    assert events[0].content == ''


def test_SYSTEM에서_sessionKey를_읽는다():
    payload = json.dumps({'type': 'connected', 'data': {'sessionKey': 'KEY-1'}})

    assert parse_system_event(payload) == 'KEY-1'


def test_connected가_아닌_SYSTEM은_무시한다():
    payload = json.dumps({'type': 'subscribed', 'data': {}})

    assert parse_system_event(payload) is None

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


def test_profile이_JSON_문자열이어도_닉네임을_읽는다():
    payload = dict(_BASE, profile=json.dumps({'nickname': '한별팬1호'}))

    assert parse_chat_event(payload)[0].nickname == '한별팬1호'


def test_profile이_객체여도_닉네임을_읽는다():
    payload = dict(_BASE, profile={'nickname': '한별팬1호', 'userRoleCode': 'common_user'})

    assert parse_chat_event(payload)[0].nickname == '한별팬1호'


def test_닉네임이_빈_문자열이면_None으로_눕힌다():
    payload = dict(_BASE, profile={'nickname': ''})

    assert parse_chat_event(payload)[0].nickname is None


def test_이모티콘_종류가_아니라_사용_횟수를_센다():
    # 2026-07-29 실측 페이로드. 자리표시자는 4개인데 emojis 키는 2개다.
    # len(emojis)로 세면 밀도가 절반으로 깎인다.
    payload = dict(
        _BASE,
        content='{:d_2:}{:lck_2:}{:lck_2:}{:lck_2:}',
        emojis={
            'd_2': 'https://ssl.pstatic.net/static/nng/glive/icon/cha02.png',
            'lck_2': 'https://ssl.pstatic.net/static/nng/glive/icon/02_t1.png',
        },
    )

    assert parse_chat_event(payload)[0].emoji_count == 4


def test_서로_다른_이모티콘_다섯_개():
    # 실측: 종류 수와 사용 횟수가 같은 경우도 정확해야 한다.
    keys = ['lck_1', 'lck_2', 'lck_3', 'lck_4', 'lck_5']
    payload = dict(
        _BASE,
        content=''.join('{:' + key + ':}' for key in keys),
        emojis={key: f'https://x/{key}.png' for key in keys},
    )

    assert parse_chat_event(payload)[0].emoji_count == 5


def test_이모티콘과_글자가_섞여도_센다():
    payload = dict(_BASE, content='{:d_2:}ㅇㅋㅋ', emojis={'d_2': 'https://a'})

    assert parse_chat_event(payload)[0].emoji_count == 1


def test_사전에_없는_자리표시자는_세지_않는다():
    # 시청자가 {:없는키:}를 직접 타이핑한 경우다. 이모티콘이 아니다.
    payload = dict(_BASE, content='{:d_2:}{:직접타이핑:}', emojis={'d_2': 'https://a'})

    assert parse_chat_event(payload)[0].emoji_count == 1


def test_이모티콘이_없으면_0이다():
    # 실측: 이모티콘을 안 쓰면 빈 사전으로 온다.
    assert parse_chat_event(dict(_BASE, emojis={}))[0].emoji_count == 0
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

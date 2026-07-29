"""치지직 Open API 호출과 CHAT 페이로드 해석.

2026-07-28 스파이크로 확인한 흐름:

    1. GET  /open/v1/sessions/auth            (Bearer 유저 토큰) -> content.url
    2. 그 URL로 socket.io 연결                (ssio*.nchat.naver.com)
    3. SYSTEM 이벤트로 sessionKey 수신
    4. POST /open/v1/sessions/events/subscribe/chat?sessionKey=...
    5. CHAT 이벤트 수신
"""
import json
import logging
from dataclasses import dataclass
from typing import Any, Optional

import httpx

from chatcollector.config import (
    CHZZK_API_BASE,
    CHZZK_LIVE_STATUS_URL,
    SPRING_TIMEOUT,
)

log = logging.getLogger(__name__)


class ChzzkError(RuntimeError):
    """치지직 API 호출이 실패했다."""


class ChzzkAuthError(ChzzkError):
    """토큰이 거부됐다. 같은 토큰으로 재시도해도 결과가 같다."""


@dataclass(frozen=True)
class ChatEvent:
    """CHAT 이벤트 한 건에서 우리가 쓰는 필드만 뽑은 것."""

    sender_channel_id: str
    content: str
    # epoch ms. sent_at의 원천이다 — eventSentAt은 쓰지 않는다(모듈 하단 주석 참고).
    message_time_ms: int
    # 'streamer' / 'manager' / 'common_user' 등. 스트리머 본인 채팅을 여론
    # 집계에서 제외할 수 있게 남긴다.
    user_role_code: Optional[str]
    # 이모티콘 밀도 입력. content와 별도 필드로 온다.
    emoji_count: int


def request_session_url(access_token: str) -> str:
    """1단계. socket.io 연결 URL을 발급받는다.

    반환 URL의 쿼리스트링에 일회용 인증값이 들어 있다. 재사용할 수 없다 —
    같은 URL로 다시 연결하면 서버가 error 이벤트에 "auth fail"을 실어 보내고
    끊는다. 재연결이 필요할 때마다 이 함수를 다시 불러야 한다.
    """
    body = _get('/open/v1/sessions/auth', access_token)
    url = (body.get('content') or {}).get('url')
    if not url:
        raise ChzzkError(f'세션 인증 응답에 url이 없습니다: {body}')
    return url


def subscribe_chat(access_token: str, session_key: str) -> None:
    """4단계. 발급받은 sessionKey로 chat 이벤트를 구독한다."""
    _post('/open/v1/sessions/events/subscribe/chat', access_token,
          params={'sessionKey': session_key})


def parse_system_event(payload: Any) -> Optional[str]:
    """3단계. SYSTEM 이벤트에서 sessionKey를 꺼낸다.

    연결 직후 말고도 오는 이벤트라 sessionKey가 없을 수 있다. 그때는 None이다.
    """
    data = _as_json(payload)
    if not isinstance(data, dict):
        return None
    if data.get('type') != 'connected':
        return None
    return (data.get('data') or {}).get('sessionKey')


def parse_chat_event(payload: Any) -> list[ChatEvent]:
    """5단계. CHAT 이벤트를 ChatEvent 목록으로 바꾼다.

    한 이벤트에 여러 건이 실려 오기도 하고 한 건만 오기도 해서 둘 다 받는다.
    해석하지 못한 건은 조용히 버리지 않고 경고를 남긴다 — 필드 이름이 바뀌면
    수집이 0건으로 떨어지는데 로그가 없으면 원인을 찾기까지 방송 하나를 날린다.
    """
    data = _as_json(payload)
    if isinstance(data, dict):
        data = [data]
    if not isinstance(data, list):
        log.warning('해석할 수 없는 CHAT 페이로드 형태: %s', type(data).__name__)
        return []

    events = []
    for raw in data:
        event = _to_chat_event(raw)
        if event is not None:
            events.append(event)
    return events


def fetch_viewer_count(channel_id: str) -> int:
    """현재 동시 시청자 수를 조회한다.

    Open API에 채널별 동시 시청자 수가 없어 서비스 API를 쓴다. 주소를
    설정으로 뺀 이유는 이 Endpoint가 공식 계약이 아니라서다.
    """
    url = CHZZK_LIVE_STATUS_URL.format(channel_id=channel_id)
    try:
        response = httpx.get(url, timeout=SPRING_TIMEOUT)
        response.raise_for_status()
        body = response.json()
    except (httpx.HTTPError, ValueError) as exception:
        raise ChzzkError(f'시청자 수 조회 실패: {exception}') from exception

    content = body.get('content')
    if not isinstance(content, dict):
        raise ChzzkError(f'시청자 수 응답 형식이 예상과 다릅니다: {body}')

    count = content.get('concurrentUserCount')
    if not isinstance(count, int):
        raise ChzzkError(f'시청자 수 필드가 없습니다: {content}')
    return count


# --- 내부 ---


def _to_chat_event(raw: Any) -> Optional[ChatEvent]:
    if not isinstance(raw, dict):
        log.warning('CHAT 항목이 객체가 아닙니다: %s', type(raw).__name__)
        return None

    sender = raw.get('senderChannelId')
    content = raw.get('content')
    message_time = raw.get('messageTime')

    if not sender or content is None or not isinstance(message_time, int):
        log.warning('CHAT 항목에 필수 필드가 없습니다: keys=%s', sorted(raw.keys()))
        return None

    # profile은 JSON 문자열로 오는 경우가 있어 한 번 더 푼다.
    profile = _as_json(raw.get('profile'))
    role = profile.get('userRoleCode') if isinstance(profile, dict) else None

    return ChatEvent(
        sender_channel_id=str(sender),
        content=str(content),
        message_time_ms=message_time,
        user_role_code=role,
        emoji_count=_count_emojis(raw.get('emojis')),
    )


def _count_emojis(emojis: Any) -> int:
    parsed = _as_json(emojis)
    if isinstance(parsed, (dict, list)):
        return len(parsed)
    return 0


def _as_json(value: Any) -> Any:
    """문자열이면 JSON으로 풀고, 아니면 그대로 돌려준다."""
    if not isinstance(value, str):
        return value
    try:
        return json.loads(value)
    except (ValueError, TypeError):
        return value


def _get(path: str, access_token: str) -> dict:
    return _call('GET', path, access_token, params=None)


def _post(path: str, access_token: str, params: Optional[dict]) -> dict:
    return _call('POST', path, access_token, params=params)


def _call(method: str, path: str, access_token: str, params: Optional[dict]) -> dict:
    try:
        response = httpx.request(
            method,
            CHZZK_API_BASE + path,
            params=params,
            headers={'Authorization': f'Bearer {access_token}'},
            timeout=SPRING_TIMEOUT,
        )
    except httpx.HTTPError as exception:
        raise ChzzkError(f'{method} {path} 실패: {exception}') from exception

    if response.status_code in (401, 403):
        raise ChzzkAuthError(f'{method} {path} 인증 거부: {response.status_code}')
    if response.status_code >= 400:
        raise ChzzkError(
            f'{method} {path} 오류 {response.status_code}: {response.text[:200]}')

    try:
        return response.json()
    except ValueError as exception:
        raise ChzzkError(f'{method} {path} 응답이 JSON이 아닙니다') from exception


# eventSentAt을 쓰지 않는 이유
#
#   "2026-07-28T11:43:32.220788801" 형태의 타임존 없는 KST 로컬 문자열이다.
#   UTC로 파싱하면 9시간 틀어지고, 그 오차는 하이라이트 구간과 채팅 리플레이가
#   방송의 엉뚱한 지점을 가리키는 형태로만 드러나서 알아채기 어렵다.
#   sent_at은 항상 messageTime(epoch ms)에서 만든다.

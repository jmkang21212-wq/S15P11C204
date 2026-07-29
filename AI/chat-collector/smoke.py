"""치지직 핸드셰이크·CHAT 페이로드 실물 확인용 프로브.

수집기가 아니다. **Spring에 아무것도 저장하지 않는다.** 치지직이 실제로
보내는 원본 페이로드를 그대로 찍어서, chzzk.py의 필드 매핑이 맞는지
눈으로 확인하는 용도다.

    # Spring에서 자격을 받아온다 (세션 행이 있어야 한다)
    python smoke.py --session-id 1

    # 토큰을 직접 넣는다 (Spring 불필요)
    python smoke.py --token <chzzk-access-token>

Ctrl+C로 끝내면 요약이 나온다.

확인할 것
    1. SYSTEM 원본에서 sessionKey가 parse_system_event로 뽑히는가
    2. CHAT 원본의 필드 이름이 senderChannelId / content / messageTime 인가
    3. profile이 문자열인가 객체인가, userRoleCode가 그 안에 있는가
    4. emojis의 형태 (dict / list / 없음)
    5. messageTime과 수신 시각의 차이가 작은가 (크면 시계 보정이 발동한다)
"""
import argparse
import json
import logging
import sys
import threading
import time

import socketio
from engineio import packet as eio_packet
from socketio import packet as sio_packet

# Windows 콘솔 기본 코드페이지(cp949)로는 출력이 깨진다. 이 스크립트는 읽으라고
# 있는 것이라 인코딩이 틀어지면 쓸모가 없다.
for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, 'reconfigure'):
        stream.reconfigure(encoding='utf-8', errors='replace')

from chatcollector import chzzk
from chatcollector.hashing import viewer_hash
from chatcollector.session import _SESSION_KEY_TIMEOUT_SEC
from chatcollector.config import SPRING_BASE_URL
from chatcollector.spring import (
    SpringClient,
    SpringRejected,
    SpringUnavailable,
    iso,
)

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
log = logging.getLogger('smoke')

seen = {'chat_events': 0, 'raw_frames': 0, 'pings': 0}


class BareConnectClient(socketio.Client):
    """CONNECT 패킷을 `0{}`이 아니라 맨 `0`으로 보낸다.

    python-socketio는 auth가 없어도 `real_auth = ... or {}`로 빈 사전을 실어
    `0{}`을 보낸다(socketio/client.py `_handle_eio_connect`). 규격상 합법이지만
    치지직 서버는 이 직후 `4{},"Invalid namespace"`를 되돌려준다.

    그 에러가 PING 실종의 원인인지 확인하기 위한 실험용 클래스다. 서버가
    연결을 반쯤 망가진 것으로 취급해 ping 타이머를 안 걸었을 가능성을 본다.
    """

    def _handle_eio_connect(self):
        self.logger.info('Engine.IO connection established (bare connect)')
        self.sid = self.eio.sid
        for namespace in self.connection_namespaces:
            # data=None이면 packet.encode()가 페이로드를 붙이지 않아 맨 `0`이 된다.
            self._send_packet(self.packet_class(
                sio_packet.CONNECT, data=None, namespace=namespace))


def count_pings(client: socketio.Client) -> None:
    """서버가 PING을 보내는지 센다.

    PING은 engine.io 계층이라 socketio 이벤트로 노출되지 않는다. 패킷 수신
    지점을 감싸서 직접 센다 — 이 값이 0이면 서버가 스펙(pingInterval)을
    광고만 하고 지키지 않는다는 뜻이다.
    """
    engine = client.eio
    original = engine._receive_packet

    def wrapped(pkt):
        if pkt.packet_type == eio_packet.PING:
            seen['pings'] += 1
            print(f'  ← PING 수신 #{seen["pings"]}')
        return original(pkt)

    engine._receive_packet = wrapped


def mask(value: str, keep: int = 6) -> str:
    """토큰과 인증 URL은 그대로 찍지 않는다. 터미널 로그가 공유될 수 있다."""
    if not value:
        return '(비어 있음)'
    if len(value) <= keep * 2:
        return value[:keep] + '…'
    return f'{value[:keep]}…{value[-keep:]} (len={len(value)})'


def dump(label: str, payload) -> None:
    print(f'\n───── {label} ─────')
    print(f'  파이썬 타입: {type(payload).__name__}')
    if isinstance(payload, str):
        print(f'  원본 문자열: {payload[:1000]}')
        try:
            parsed = json.loads(payload)
            print('  JSON 파싱 결과:')
            print(_indent(json.dumps(parsed, ensure_ascii=False, indent=2)))
        except ValueError:
            print('  (JSON이 아니다)')
    else:
        print(_indent(json.dumps(payload, ensure_ascii=False, indent=2, default=str)))


def _indent(text: str) -> str:
    return '\n'.join('    ' + line for line in text.splitlines())


def on_chat(payload) -> None:
    seen['raw_frames'] += 1
    received_at = time.time()

    dump(f'CHAT 원본 #{seen["raw_frames"]}', payload)

    events = chzzk.parse_chat_event(payload)
    if not events:
        print('  ⚠ parse_chat_event가 0건을 돌려줬다 — 필드 이름이 바뀌었을 수 있다')
        return

    print(f'  → 해석 결과 {len(events)}건:')
    for event in events:
        seen['chat_events'] += 1
        skew = event.message_time_ms / 1000.0 - received_at
        flag = '  ⚠ 시계 보정 발동 범위' if abs(skew) > 300 else ''
        print(f'''    content        : {event.content!r}
    viewerHash     : {viewer_hash(event.sender_channel_id)}
    sent_at        : {iso(event.message_time_ms / 1000.0)}
    수신 시각 대비  : {skew:+.1f}초{flag}
    userRoleCode   : {event.user_role_code}
    emoji_count    : {event.emoji_count}''')


def main() -> int:
    parser = argparse.ArgumentParser(description='치지직 수집 스모크 프로브')
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--session-id', type=int, help='Spring에서 자격을 받아온다')
    source.add_argument('--token', help='치지직 Access Token을 직접 넣는다')
    parser.add_argument(
        '--verbose', action='store_true',
        help='engine.io 프레임까지 찍는다. 끊기는 원인을 볼 때 쓴다 '
             '(핸드셰이크의 pingInterval·pingTimeout과 종료 이유가 보인다)')
    parser.add_argument(
        '--bare-connect', action='store_true',
        help='CONNECT 패킷을 `0{}` 대신 맨 `0`으로 보낸다. 치지직이 되돌려주는 '
             '"Invalid namespace" 에러가 PING 실종의 원인인지 확인하는 실험이다')
    args = parser.parse_args()

    if args.verbose:
        # socketio는 이벤트, engineio는 전송 계층(ping/pong, close)을 찍는다.
        # 주기적으로 끊기는 원인은 대개 engineio 쪽에 있다.
        logging.getLogger('socketio').setLevel(logging.DEBUG)
        logging.getLogger('engineio').setLevel(logging.DEBUG)

    # 1. 자격
    if args.token:
        access_token, channel_id = args.token, None
        print(f'토큰 직접 입력: {mask(access_token)}')
    else:
        spring = SpringClient()
        try:
            access_token, channel_id = spring.fetch_credentials(args.session_id)
        except SpringUnavailable:
            print(f'''✗ Spring({SPRING_BASE_URL})에 닿지 못했습니다.

  Spring이 떠 있는지 확인하세요:
      cd BackEnd/spring/laive && ./gradlew bootRun

  주소가 다르면 SPRING_BASE_URL로 바꿀 수 있습니다.''')
            return 1
        except SpringRejected as exception:
            print(f'''✗ Spring이 거부했습니다: {exception}

  401 -> .env의 INTERNAL_SERVICE_TOKEN과 환경변수 SPRING_TOKEN이 같은지 확인
  404 -> sessionId {args.session_id} 행이 broadcast_sessions에 없음''')
            return 1
        finally:
            spring.close()
        print(f'✓ 1단계 Spring 자격 조회: token={mask(access_token)} channelId={channel_id}')

    # 2. 인증 URL
    url = chzzk.request_session_url(access_token)
    print(f'✓ 2단계 세션 인증 URL: {mask(url, 30)}')

    # 3. socket.io 연결 + SYSTEM
    session_key = {}
    received = threading.Event()
    client_class = BareConnectClient if args.bare_connect else socketio.Client
    print(f'CONNECT 패킷: {"맨 0 (실험)" if args.bare_connect else "0{} (기본)"}')
    client = client_class(
        reconnection=False,
        logger=args.verbose,
        engineio_logger=args.verbose,
    )
    connected_at = {'value': None}

    @client.on('disconnect')
    def _on_disconnect(*payload):  # noqa: ANN001
        # 실측: 치지직은 약 88초 주기로 연결을 닫는다. 얼마나 버텼는지가
        # 원인 추적의 핵심 단서다 — 매번 같은 값이면 서버 타임아웃이고,
        # 들쭉날쭉하면 네트워크다.
        held = ('?' if connected_at['value'] is None
                else f'{time.time() - connected_at["value"]:.1f}초')
        print(f'\n⚠ 연결 끊김 — 유지 시간 {held}, 인자={payload!r}')

    @client.on('SYSTEM')
    def _on_system(payload):  # noqa: ANN001
        dump('SYSTEM 원본', payload)
        key = chzzk.parse_system_event(payload)
        print(f'  → parse_system_event: {key!r}')
        if key:
            session_key['value'] = key
            received.set()

    @client.on('CHAT')
    def _on_chat(payload):  # noqa: ANN001
        on_chat(payload)

    @client.on('error')
    def _on_error(payload):  # noqa: ANN001
        print(f'\n⚠ error 이벤트: {payload!r}')

    client.connect(url, transports=['websocket'])
    connected_at['value'] = time.time()
    count_pings(client)
    engine = client.eio
    print(f'''✓ 3단계 socket.io 연결됨
    engine.io 프로토콜 : EIO={getattr(engine, "protocol", "?")}
    pingInterval       : {getattr(engine, "ping_interval", "?")}초
    pingTimeout        : {getattr(engine, "ping_timeout", "?")}초''')

    if not received.wait(timeout=_SESSION_KEY_TIMEOUT_SEC):
        print('✗ SYSTEM에서 sessionKey를 못 받았다')
        client.disconnect()
        return 1
    print(f'✓ sessionKey 수신: {mask(session_key["value"])}')

    # 4. 구독
    chzzk.subscribe_chat(access_token, session_key['value'])
    print('✓ 4단계 chat 구독 완료')

    # 5. 시청자 수 (channelId를 아는 경우만)
    if channel_id:
        try:
            print(f'✓ 시청자 수 조회: {chzzk.fetch_viewer_count(channel_id)}명')
        except chzzk.ChzzkError as exception:
            print(f'⚠ 시청자 수 조회 실패: {exception}')

    print('\n대기 중 — 방송 채팅창에 아무 말이나 쳐보세요. Ctrl+C로 종료.')
    print('(끊길 때까지 얼마나 버티는지도 함께 봅니다. 프로브는 재연결하지 않습니다.)\n')
    try:
        while client.connected:
            time.sleep(0.5)
        # 프로브는 재핸드셰이크를 하지 않는다. 끊긴 시점을 그대로 보여주는 것이
        # 목적이라, 수집기처럼 되살리면 원인이 로그에 묻힌다.
        print('연결이 닫혀서 프로브를 종료합니다.')
    except KeyboardInterrupt:
        pass
    finally:
        client.disconnect()

    held = ('?' if connected_at['value'] is None
            else f'{time.time() - connected_at["value"]:.1f}초')
    print(f'''
요약
    연결 유지     : {held}
    CHAT 프레임   : {seen["raw_frames"]}개 (해석된 채팅 {seen["chat_events"]}건)
    PING 수신     : {seen["pings"]}회''')

    if seen['pings'] == 0:
        print(f'''
  ⚠ PING이 0회다. 서버가 pingInterval {getattr(engine, "ping_interval", "?")}초를
    광고하고 지키지 않는다는 뜻이다. 클라이언트는 보낼 PONG이 없어 쓰기 큐가
    굶고, max(pingInterval, pingTimeout)+5초에 연결을 포기한다.''')
    else:
        print(f'''
  ✓ PING이 {seen["pings"]}회 왔다. 위 CONNECT 방식에서는 서버가 ping을 보낸다 —
    끊김의 원인이 CONNECT 패킷이었다는 뜻이다.''')
    return 0


if __name__ == '__main__':
    sys.exit(main())

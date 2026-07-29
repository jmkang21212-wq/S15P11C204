"""세션 하나를 담당하는 수집 워커.

왜 전용 스레드인가
    치지직 socket.io는 자동 재연결이 불가능하다. 세션 인증 URL의 쿼리스트링
    인증값이 재사용되지 않아 같은 URL로 재연결하면 서버가 error 이벤트에
    "auth fail"을 실어 보내고 끊는다. 복구하려면 자격 조회 -> 인증 URL 발급 ->
    connect -> SYSTEM sessionKey -> subscribe 전체를 다시 밟아야 하고 이전
    sessionKey는 버린다.

    그래서 sessionKey, 재핸드셰이크 백오프, 열려 있는 공백 구간을 세션이 끝날
    때까지 들고 사는 실행 주체가 필요하다. 주기 실행(@Scheduled 류)으로는
    이 상태를 이어갈 수 없다.

왜 reconnection=False인가
    python-socketio의 자동 재연결에 맡기면 위 이유로 매번 auth fail이 나는데,
    라이브러리는 그것을 그냥 "재연결 시도 중"으로 로그에 남긴다. 첫 끊김에서
    조용히 죽으면서 겉보기에는 정상 재시도처럼 보여 장애 발견이 늦어진다.
"""
import logging
import queue
import threading
import time
from typing import Optional

import socketio
from engineio import packet as eio_packet

from chatcollector import chzzk
from chatcollector.config import (
    CHAT_BATCH_SEC,
    CHAT_BATCH_SIZE,
    CHAT_QUEUE_MAX,
    HEARTBEAT_SEC,
    MESSAGE_TIME_MAX_SKEW_SEC,
    RECONNECT_BACKOFF_MAX_SEC,
    RECONNECT_BACKOFF_START_SEC,
    SOCKET_PING_SEC,
    VIEWER_POLL_SEC,
    VIEWER_STALE_AFTER_SEC,
)
from chatcollector.hashing import viewer_hash
from chatcollector.spring import SpringClient, SpringRejected, iso

log = logging.getLogger(__name__)


class _SuppressUnexpectedPong(logging.Filter):
    """우리 PING에 대한 PONG을 engineio가 ERROR로 찍는 것을 지운다.

    EIO v4는 클라이언트가 PONG을 받을 일이 없다고 보므로 engineio가
    "Received unexpected packet of type 3"을 ERROR로 남긴다. 치지직이 ping을
    보내지 않아 우리가 대신 보내는 이상 하트비트마다 한 줄씩 쌓인다 —
    20초 주기면 4시간 방송에 720줄이고, 그 사이에서 진짜 오류를 찾을 수 없게 된다.

    PONG 수신은 우리가 의도한 정상 동작이므로 이 한 줄만 걸러낸다.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        return 'unexpected packet of type 3' not in record.getMessage()


logging.getLogger('engineio.client').addFilter(_SuppressUnexpectedPong())


class _ChzzkSocketClient(socketio.Client):
    """치지직의 규격 위반 패킷에 배경 스레드가 죽지 않게 한다.

    치지직은 우리 CONNECT `0{}`의 `{}`를 auth 페이로드가 아니라 **namespace로**
    잘못 읽고 이렇게 되돌려준다.

        4{},"Invalid namespace"

    socket.io 규격이라면 타입 문자 `4` 뒤에는 JSON 하나가 와야 하는데 `{}`와
    `"Invalid namespace"` 둘이 붙어 있다. python-socketio의 json.loads가
    "Extra data"로 깨지고, 이 디코딩이 배경 스레드에서 일어나므로 연결마다
    처리되지 않은 스택트레이스가 찍힌다.

    연결·구독·CHAT 수신은 모두 정상이라 이 패킷은 무시해도 된다. 하지만
    스택트레이스를 그대로 두면 진짜 오류와 섞여서 로그를 못 읽는다.

    디코딩만 미리 시험하고 실패한 것만 버린다. super()에 그대로 넘기면서
    try로 감싸는 방식은 쓰지 않는다 — 그러면 CHAT 핸들러 안에서 난 ValueError
    까지 함께 삼켜 우리 버그를 숨긴다.
    """

    # 세션이 계수기를 붙인다. 관측만 하고 동작에는 영향이 없다.
    on_malformed_packet = None

    def _handle_eio_message(self, data):
        # 바이너리 첨부는 패킷으로 디코딩할 대상이 아니다.
        if not self._binary_packet and isinstance(data, str):
            try:
                self.packet_class(encoded_packet=data)
            except ValueError as exception:
                if self.on_malformed_packet is not None:
                    self.on_malformed_packet()
                log.debug('치지직 규격 위반 패킷 무시: %r (%s)',
                          data[:120], exception)
                return
        super()._handle_eio_message(data)

# Spring의 CollectorState와 같은 값이어야 한다.
RUNNING = 'RUNNING'
RECONNECTING = 'RECONNECTING'
STOPPED = 'STOPPED'

# collection_gaps.reason CHECK 제약(V9)과 같은 값이어야 한다.
GAP_DISCONNECTED = 'DISCONNECTED'
GAP_AUTH_FAILED = 'AUTH_FAILED'
GAP_SUBSCRIBE_FAILED = 'SUBSCRIBE_FAILED'

# SYSTEM 이벤트로 sessionKey가 오기를 기다리는 시간.
_SESSION_KEY_TIMEOUT_SEC = 10.0


class CollectorSession:
    """방송 세션 하나의 채팅·시청자 수 수집을 담당한다."""

    def __init__(self, session_id: int, spring: SpringClient):
        self.session_id = session_id
        self._spring = spring

        self._stop = threading.Event()
        self._threads: list[threading.Thread] = []
        self._chat: queue.Queue = queue.Queue(maxsize=CHAT_QUEUE_MAX)

        self._lock = threading.Lock()
        self._state = STOPPED
        self._gap_started_at: Optional[float] = None
        self._gap_reason: Optional[str] = None
        self._sio: Optional[socketio.Client] = None

        self._channel_id: Optional[str] = None
        self._last_viewer_count: Optional[int] = None
        self._last_viewer_at: Optional[float] = None

        # 관측용 카운터. 13.3.1에 자리가 없어 저장하지 못하는 값도 여기서는 센다.
        self.received = 0
        self.sent = 0
        self.dropped = 0
        self.clock_skew_corrected = 0
        self.streamer_messages = 0
        self.emoji_total = 0
        # 치지직이 보내는 규격 위반 패킷 수. 연결마다 1건이 정상이다
        # (_ChzzkSocketClient 주석 참고). 갑자기 늘면 서버 쪽이 바뀐 것이다.
        self.malformed_packets = 0

    def _count_malformed_packet(self) -> None:
        self.malformed_packets += 1

    # --- 생명주기 ---

    def start(self) -> None:
        if self._threads:
            return
        self._threads = [
            threading.Thread(target=self._collect_loop, daemon=True,
                             name=f'collect-{self.session_id}'),
            threading.Thread(target=self._flush_loop, daemon=True,
                             name=f'flush-{self.session_id}'),
            threading.Thread(target=self._viewer_loop, daemon=True,
                             name=f'viewers-{self.session_id}'),
            # 하트비트를 시청자 폴링과 합치지 않는 이유: 폴링 HTTP가 느려지면
            # 하트비트까지 밀려 Spring이 살아 있는 수집기를 STOPPED로 표시한다.
            threading.Thread(target=self._heartbeat_loop, daemon=True,
                             name=f'heartbeat-{self.session_id}'),
        ]
        for thread in self._threads:
            thread.start()
        log.info('수집 시작: sessionId=%s', self.session_id)

    def stop(self, grace_period_sec: float = 30.0) -> None:
        """수집을 멈추고 남은 채팅을 Flush한다."""
        self._stop.set()
        self._disconnect()

        for thread in self._threads:
            thread.join(timeout=grace_period_sec)
        self._threads = []

        # 남은 큐를 마저 보낸다. 방송 마지막 순간의 채팅이 리포트에서 빠지면 안 된다.
        self._flush_pending()
        self._set_state(STOPPED)
        self._report_status_safely()
        log.info('수집 종료: sessionId=%s received=%s sent=%s dropped=%s',
                 self.session_id, self.received, self.sent, self.dropped)

    def snapshot(self) -> dict:
        with self._lock:
            state = self._state
            gap_started_at = self._gap_started_at
            last_viewer_count = self._last_viewer_count
            last_viewer_at = self._last_viewer_at

        viewer_age = None if last_viewer_at is None else time.time() - last_viewer_at
        return {
            'sessionId': self.session_id,
            'state': state,
            'queued': self._chat.qsize(),
            'received': self.received,
            'sent': self.sent,
            'dropped': self.dropped,
            'clockSkewCorrected': self.clock_skew_corrected,
            'malformedPackets': self.malformed_packets,
            # 13.3.1에 필드가 없어 Spring으로는 보내지 못하는 값이다.
            'streamerMessages': self.streamer_messages,
            'emojiTotal': self.emoji_total,
            'openGapStartedAt': None if gap_started_at is None else iso(gap_started_at),
            'lastViewerCount': last_viewer_count,
            'lastViewerAgeSec': viewer_age,
            # 값이 너무 낡았는지만 본다. HIGHLIGHT_SPEC 가드 4의 5분 기준이다.
            'viewerStale': viewer_age is None or viewer_age > VIEWER_STALE_AFTER_SEC,
            # 판정에 써도 되는지의 최종 답. 가드 4는 낡은 값 금지와 "0으로 나누지
            # 말 것"을 함께 요구하는데, viewerStale만 보면 시청자 0명을 방금
            # 정상 측정한 상태가 "쓸 수 있음"으로 통과해 engagement 계산이
            # 0으로 나눈다. 실제로 방송을 켜고 혼자 확인하면 치지직
            # concurrentUserCount가 0으로 오므로 드문 경우가 아니다.
            'viewerUsable': (
                last_viewer_count is not None
                and last_viewer_count > 0
                and viewer_age is not None
                and viewer_age <= VIEWER_STALE_AFTER_SEC
            ),
        }

    # --- 수집 루프 ---

    def _collect_loop(self) -> None:
        """끊길 때마다 전체 핸드셰이크를 처음부터 다시 한다."""
        backoff = RECONNECT_BACKOFF_START_SEC

        while not self._stop.is_set():
            reason = GAP_DISCONNECTED
            try:
                self._handshake_and_receive()
                # 정상 구독까지 갔다가 끊긴 경우다. 다음 시도는 짧게 기다린다.
                backoff = RECONNECT_BACKOFF_START_SEC
            except chzzk.ChzzkAuthError as exception:
                reason = GAP_AUTH_FAILED
                log.warning('치지직 인증 거부: sessionId=%s %s',
                            self.session_id, exception)
            except chzzk.ChzzkError as exception:
                reason = GAP_SUBSCRIBE_FAILED
                log.warning('핸드셰이크 실패: sessionId=%s %s',
                            self.session_id, exception)
            except Exception as exception:  # noqa: BLE001 - 루프를 죽이지 않는다
                log.exception('수집 루프 오류: sessionId=%s %s',
                              self.session_id, exception)
            finally:
                self._disconnect()

            if self._stop.is_set():
                break

            self._open_gap(reason)
            self._stop.wait(backoff)
            backoff = min(backoff * 2, RECONNECT_BACKOFF_MAX_SEC)

    def _make_socket_client(self) -> _ChzzkSocketClient:
        """CHAT과 error 핸들러가 붙은 socket.io 클라이언트를 만든다.

        SYSTEM은 sessionKey 대기와 묶여 있어 핸드셰이크 쪽에서 등록한다.
        """
        client = _ChzzkSocketClient(reconnection=False)
        client.on_malformed_packet = self._count_malformed_packet

        @client.on('CHAT')
        def _on_chat(payload):  # noqa: ANN001
            self._on_chat(payload)

        @client.on('error')
        def _on_error(payload):  # noqa: ANN001
            # 재사용된 인증 URL로 붙으면 여기로 "auth fail"이 온다.
            log.warning('치지직 error 이벤트: sessionId=%s %s',
                        self.session_id, payload)

        return client

    def _handshake_and_receive(self) -> None:
        # 자격은 매번 새로 받는다. 들고 있던 Access Token이 만료되면 이후
        # 재핸드셰이크가 전부 401로 실패해 남은 방송 내내 수집이 죽는다.
        access_token, channel_id = self._spring.fetch_credentials(self.session_id)
        with self._lock:
            self._channel_id = channel_id

        url = chzzk.request_session_url(access_token)

        session_key: dict = {}
        received_key = threading.Event()
        client = self._make_socket_client()

        @client.on('SYSTEM')
        def _on_system(payload):  # noqa: ANN001
            key = chzzk.parse_system_event(payload)
            if key:
                session_key['value'] = key
                received_key.set()

        client.connect(url, transports=['websocket'])
        with self._lock:
            self._sio = client

        if not received_key.wait(timeout=_SESSION_KEY_TIMEOUT_SEC):
            raise chzzk.ChzzkError('SYSTEM 이벤트에서 sessionKey를 받지 못했습니다')

        chzzk.subscribe_chat(access_token, session_key['value'])
        self._close_gap()
        log.info('채팅 구독 완료: sessionId=%s', self.session_id)

        # 하트비트 없이는 서버가 ping을 보내지 않아 85초 안에 반드시 끊긴다.
        ping_stop = threading.Event()
        ping_thread = threading.Thread(
            target=self._socket_ping_loop, args=(client, ping_stop),
            daemon=True, name=f'socket-ping-{self.session_id}')
        ping_thread.start()

        try:
            # 끊길 때까지 여기서 대기한다. 이벤트는 socketio 내부 스레드가 배달한다.
            client.wait()
        finally:
            ping_stop.set()

    def _socket_ping_loop(self, client: socketio.Client,
                          stop: threading.Event) -> None:
        """engine.io PING을 주기적으로 보낸다.

        치지직은 pingInterval을 광고하고 PING을 보내지 않는다(2026-07-29 실측).
        그러면 python-engineio의 쓰기 루프가 65초, 읽기 루프가 85초에 연결을
        포기한다. 우리가 PING을 보내면 서버가 PONG으로 답하므로 보낼 것과 받을
        것이 동시에 생겨 두 루프가 모두 살아 있다.

        _send_packet은 비공개 API다. EIO v4에서 클라이언트가 PING을 보내는 것은
        규격 밖이라 공개 경로가 없다. python-socketio를 올릴 때 이 호출이
        살아 있는지 확인해야 한다.
        """
        while not stop.wait(SOCKET_PING_SEC):
            try:
                client.eio._send_packet(eio_packet.Packet(eio_packet.PING))
            except Exception as exception:  # noqa: BLE001
                # 이미 끊긴 뒤일 수 있다. 재핸드셰이크는 _collect_loop가 맡는다.
                log.debug('socket.io PING 송신 중단: sessionId=%s %s',
                          self.session_id, exception)
                return

    def _on_chat(self, payload) -> None:  # noqa: ANN001
        received_at = time.time()
        for event in chzzk.parse_chat_event(payload):
            self.received += 1

            if event.user_role_code in ('streamer', 'manager'):
                self.streamer_messages += 1
            self.emoji_total += event.emoji_count

            self._offer({
                'viewerHash': viewer_hash(event.sender_channel_id),
                'message': event.content,
                'sentAt': iso(self._sent_at(event.message_time_ms, received_at)),
            })

    def _sent_at(self, message_time_ms: int, received_at: float) -> float:
        """sent_at을 정한다.

        messageTime(epoch ms)을 쓴다. eventSentAt은 타임존 없는 KST 로컬
        문자열이라 UTC로 파싱하면 9시간 틀어진다.

        다만 messageTime은 클라이언트 시계다(HIGHLIGHT_SPEC 가드 8). 서버 수신
        시각과 너무 벌어지면 수신 시각으로 대체한다 — 어긋난 시계 하나가
        chat_messages의 먼 과거·미래 청크에 행을 만들고 리플레이 정렬을 깬다.
        """
        sent_at = message_time_ms / 1000.0
        if abs(sent_at - received_at) > MESSAGE_TIME_MAX_SKEW_SEC:
            self.clock_skew_corrected += 1
            return received_at
        return sent_at

    def _offer(self, item: dict) -> None:
        try:
            self._chat.put_nowait(item)
        except queue.Full:
            # Spring이 오래 죽어 있는 상황. 오래된 것부터 버려 최신을 살린다.
            try:
                self._chat.get_nowait()
                self.dropped += 1
                self._chat.put_nowait(item)
            except (queue.Empty, queue.Full):
                self.dropped += 1

    # --- 전송 루프 ---

    def _flush_loop(self) -> None:
        batch: list[dict] = []
        last_flush = time.monotonic()

        while not self._stop.is_set():
            try:
                batch.append(self._chat.get(timeout=0.2))
            except queue.Empty:
                pass

            due = (len(batch) >= CHAT_BATCH_SIZE
                   or (batch and time.monotonic() - last_flush >= CHAT_BATCH_SEC))
            if due:
                self._send_chat(batch)
                batch = []
                last_flush = time.monotonic()

        if batch:
            self._send_chat(batch)

    def _flush_pending(self) -> None:
        batch: list[dict] = []
        while True:
            try:
                batch.append(self._chat.get_nowait())
            except queue.Empty:
                break
            if len(batch) >= CHAT_BATCH_SIZE:
                self._send_chat(batch)
                batch = []
        if batch:
            self._send_chat(batch)

    def _send_chat(self, batch: list[dict]) -> None:
        try:
            self._spring.send_chat_batch(self.session_id, batch)
            self.sent += len(batch)
        except SpringRejected as exception:
            # 4xx는 재시도해도 같은 결과다. 이 배치는 버린다.
            self.dropped += len(batch)
            log.error('Spring이 채팅 배치를 거부했습니다: sessionId=%s %s',
                      self.session_id, exception)
        except Exception as exception:  # noqa: BLE001
            self.dropped += len(batch)
            log.error('채팅 배치 전송 실패: sessionId=%s %s',
                      self.session_id, exception)

    # --- 시청자 수 폴링 ---

    def _viewer_loop(self) -> None:
        """시청자 수를 주기적으로 조회해 보낸다.

        폴링에 실패한 틱은 아무것도 보내지 않는다. 마지막 정상값을 새 측정치로
        다시 써서 보내면 viewer_metrics에 진짜와 구분되지 않는 합성 행이 쌓이고,
        리포트의 viewerTimeline과 평균·최고 시청자가 조용히 왜곡된다.
        HIGHLIGHT_SPEC 가드 4의 "마지막 정상값 최대 5분"은 저장 계층이 아니라
        판정 계층의 규칙이라, 마지막 정상값과 그 나이를 snapshot()으로 노출해
        판정이 같은 기준을 적용하게 한다.
        """
        while not self._stop.wait(VIEWER_POLL_SEC):
            with self._lock:
                channel_id = self._channel_id
            if not channel_id:
                continue

            measured_at = time.time()
            try:
                count = chzzk.fetch_viewer_count(channel_id)
            except chzzk.ChzzkError as exception:
                log.warning('시청자 수 조회 실패: sessionId=%s %s',
                            self.session_id, exception)
                continue

            with self._lock:
                self._last_viewer_count = count
                self._last_viewer_at = measured_at

            try:
                self._spring.send_viewer_metrics(self.session_id, [{
                    'measuredAt': iso(measured_at),
                    'viewerCount': count,
                }])
            except Exception as exception:  # noqa: BLE001
                log.warning('시청자 수 전송 실패: sessionId=%s %s',
                            self.session_id, exception)

    # --- 상태 보고 ---

    def _heartbeat_loop(self) -> None:
        self._report_status_safely()
        while not self._stop.wait(HEARTBEAT_SEC):
            self._report_status_safely()

    def _report_status_safely(self) -> None:
        with self._lock:
            state = self._state
        try:
            self._spring.report_status(self.session_id, state, time.time())
        except Exception as exception:  # noqa: BLE001
            log.warning('수집기 상태 보고 실패: sessionId=%s %s',
                        self.session_id, exception)

    def _set_state(self, state: str) -> None:
        with self._lock:
            self._state = state

    # --- 공백 구간 ---

    def _open_gap(self, reason: str) -> None:
        """끊긴 구간을 연다. 이미 열려 있으면 아무것도 하지 않는다."""
        with self._lock:
            if self._gap_started_at is not None:
                self._state = RECONNECTING
                return
            started_at = time.time()
            self._gap_started_at = started_at
            self._gap_reason = reason
            self._state = RECONNECTING

        log.warning('수집 공백 시작: sessionId=%s reason=%s', self.session_id, reason)
        try:
            self._spring.report_gap(self.session_id, started_at, None, reason)
        except Exception as exception:  # noqa: BLE001
            log.error('수집 공백 보고 실패: sessionId=%s %s', self.session_id, exception)

    def _close_gap(self) -> None:
        """구독을 회복했다. 열려 있던 구간을 닫는다."""
        with self._lock:
            started_at = self._gap_started_at
            # 구간을 연 이유를 그대로 쓴다. Upsert가 ended_at만 갱신하므로 값이
            # 달라도 저장된 reason은 안 바뀌지만, 로그와 보고가 어긋나면
            # 나중에 원인을 추적할 때 헷갈린다.
            reason = self._gap_reason or GAP_DISCONNECTED
            self._gap_started_at = None
            self._gap_reason = None
            self._state = RUNNING

        if started_at is None:
            return

        ended_at = time.time()
        log.warning('수집 공백 종료: sessionId=%s reason=%s 유실 구간 %.1f초',
                    self.session_id, reason, ended_at - started_at)
        try:
            self._spring.report_gap(
                self.session_id, started_at, ended_at, reason)
        except Exception as exception:  # noqa: BLE001
            # 닫지 못해도 세션 종료 시 Spring이 종료 시각으로 닫는다(V9).
            log.error('수집 공백 종료 보고 실패: sessionId=%s %s',
                      self.session_id, exception)

    def _disconnect(self) -> None:
        with self._lock:
            client, self._sio = self._sio, None
        if client is None:
            return
        try:
            client.disconnect()
        except Exception:  # noqa: BLE001
            pass

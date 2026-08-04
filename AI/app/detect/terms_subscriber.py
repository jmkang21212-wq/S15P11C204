"""개인 사전 변경을 방송 중에 받아 적용한다 (AI/RISK_POLICY_SPEC.md §3).

티켓은 접속 순간의 사전을 실어 오므로, 방송을 켜둔 채 설정을 고치면 재접속 전까지
반영되지 않는다. Spring이 등록·삭제를 커밋한 뒤 `voice:terms:<streamerId>`로 발행하고,
여기서 받아 살아있는 세션의 사전을 갈아끼운다.

**전달 보장이 없다.** Redis pub/sub은 구독자가 없거나 끊겨 있으면 메시지를 버린다.
놓쳐도 다음 방송에는 티켓으로 정상 반영되므로 재전송이나 ACK를 만들지 않는다.
"""

import logging
import threading
from typing import Callable, Optional

import redis as redis_lib

from app.config import VOICE_REDIS_URL
from app.detect.personal import PersonalTerms

log = logging.getLogger(__name__)

# Spring의 RiskTermBroadcaster와 맞춰야 한다. 한쪽만 바꾸면 조용히 반영이 멈춘다.
CHANNEL_PATTERN = 'voice:terms:*'
CHANNEL_PREFIX = 'voice:terms:'


class TermsSubscriber:
    """구독 스레드. Redis가 없거나 죽어 있으면 조용히 비활성 상태로 남는다.

    실시간 반영은 편의 기능이라 여기서 실패해도 서버 기동을 막지 않는다 —
    사전은 접속 시 티켓으로 이미 전달됐다.
    """

    def __init__(self, apply: Callable[[int, PersonalTerms], None],
                 redis_url: str = VOICE_REDIS_URL):
        self._apply = apply
        self._redis_url = redis_url
        self._thread: Optional[threading.Thread] = None
        self._pubsub = None
        self._stopping = threading.Event()

    def start(self) -> "TermsSubscriber":
        self._thread = threading.Thread(target=self._run, name='terms-sub', daemon=True)
        self._thread.start()
        return self

    def stop(self, timeout: float = 2.0) -> None:
        self._stopping.set()
        # get_message는 타임아웃으로 깨어나므로 close만으로 충분하다.
        pubsub = self._pubsub
        if pubsub is not None:
            try:
                pubsub.close()
            except Exception:      # noqa: BLE001 — 종료 경로에서 더 할 일이 없다
                pass
        if self._thread is not None:
            self._thread.join(timeout=timeout)

    # --- 내부 ---

    def _run(self) -> None:
        try:
            client = redis_lib.Redis.from_url(self._redis_url, socket_timeout=None,
                                              socket_connect_timeout=2)
            self._pubsub = client.pubsub(ignore_subscribe_messages=True)
            self._pubsub.psubscribe(CHANNEL_PATTERN)
            log.info('개인 사전 변경 구독 시작: %s', CHANNEL_PATTERN)
        except redis_lib.RedisError as e:
            log.warning('개인 사전 구독 실패(%s) — 방송 중 변경은 반영되지 않는다. '
                        '다음 방송에는 티켓으로 정상 반영된다', type(e).__name__)
            return

        while not self._stopping.is_set():
            try:
                message = self._pubsub.get_message(timeout=1.0)
            except redis_lib.RedisError as e:
                if self._stopping.is_set():
                    return
                log.warning('개인 사전 구독이 끊겼다(%s) — 재구독을 시도한다',
                            type(e).__name__)
                if not self._resubscribe():
                    return
                continue
            except Exception:      # noqa: BLE001 — 종료 중 소켓이 닫힌 경우
                return

            if message is not None:
                self._handle(message)

    def _resubscribe(self) -> bool:
        try:
            self._pubsub.psubscribe(CHANNEL_PATTERN)
            return True
        except redis_lib.RedisError:
            log.warning('개인 사전 재구독 실패 — 구독을 포기한다')
            return False

    def _handle(self, message: dict) -> None:
        streamer_id = _streamer_id_of(message.get('channel'))
        if streamer_id is None:
            return
        terms = PersonalTerms.parse_json(message.get('data') or '')
        try:
            self._apply(streamer_id, terms)
        except Exception:          # noqa: BLE001 — 한 건의 실패로 구독을 멈추지 않는다
            log.exception('개인 사전 적용 실패 streamerId=%s', streamer_id)


def _streamer_id_of(channel: object) -> Optional[int]:
    if isinstance(channel, bytes):
        try:
            channel = channel.decode()
        except UnicodeDecodeError:
            return None
    if not isinstance(channel, str) or not channel.startswith(CHANNEL_PREFIX):
        return None
    try:
        return int(channel[len(CHANNEL_PREFIX):])
    except ValueError:
        return None

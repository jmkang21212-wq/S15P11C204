"""
WebSocket 접속 인증 — Spring이 발급한 일회용 티켓을 검증한다.

왜 티켓인가: WebSocket은 브라우저에서 Authorization 헤더를 실을 수 없어 JWT를
직접 쓰기 어렵다. 그래서 프론트가 인증된 HTTP로 짧은 티켓을 받아 핸드셰이크에 싣고,
여기서 Redis를 조회해 확인한다. Spring과 AI가 이미 같은 Redis를 쓰므로 공개키 교환이나
서명 알고리즘 합의가 필요 없다.

**이 모듈의 핵심은 인증보다 무결성이다.** 이전에는 클라이언트가 핸드셰이크에 실어 보낸
broadcastSessionId를 그대로 믿어서, 아무 번호나 넣으면 남의 방송에 전사·위험 이벤트를
기록할 수 있었다. 이제 그 번호를 **티켓에서** 꺼내므로 구조적으로 막힌다.

Redis DB 주의: Spring이 티켓을 쓰므로 **Spring의 DB(기본 0)** 를 읽어야 한다.
채팅 감정 서버가 쓰는 DB 1이 아니다.
"""
import json
import logging
from typing import NamedTuple, Optional

import redis as redis_lib

from app.config import VOICE_AUTH_DEV_BYPASS, VOICE_REDIS_URL
from app.detect.personal import EMPTY, PersonalTerms

log = logging.getLogger(__name__)

# Spring의 VoiceTicketService와 맞춰야 한다. 한쪽만 바꾸면 조용히 인증이 전부 실패한다.
TICKET_KEY_PREFIX = 'voice:ticket:'
TICKET_DELIMITER = '|'


class Ticket(NamedTuple):
    streamer_id: int
    broadcast_session_id: int
    # 스트리머 개인 사전. 구형식 티켓이면 비어 있다.
    terms: PersonalTerms = EMPTY


class TicketVerifier:
    """티켓 검증기. Redis가 없거나 죽어 있으면 모든 접속을 거절한다(fail-closed)."""

    def __init__(self, redis_url: str = VOICE_REDIS_URL,
                 dev_bypass: bool = VOICE_AUTH_DEV_BYPASS):
        self.dev_bypass = dev_bypass
        self._r: Optional[redis_lib.Redis] = None
        try:
            self._r = redis_lib.Redis.from_url(
                redis_url, socket_timeout=2, socket_connect_timeout=2)
            self._r.ping()
            log.info('티켓 검증 활성화: %s', redis_url)
        except redis_lib.RedisError as e:
            # 기동은 막지 않는다 — Redis가 늦게 떠도 첫 접속 때 자동으로 회복된다.
            log.warning('티켓 검증용 Redis 접속 실패(%s: %s) — 접속 시점에 재시도한다',
                        redis_url, type(e).__name__)
        if dev_bypass:
            log.warning('VOICE_AUTH_DEV_BYPASS=1 — 티켓 없이 접속을 허용한다. '
                        '배포 환경에서는 절대 켜지 말 것')

    def consume(self, ticket: str) -> Optional[Ticket]:
        """티켓을 검증하고 소비한다(1회용). 유효하지 않으면 None.

        getdel을 쓰는 이유: 조회와 삭제가 원자적이라 같은 티켓으로 두 번 접속하는
        경합이 생기지 않는다.
        """
        if not ticket or self._r is None:
            return None
        try:
            raw = self._r.getdel(TICKET_KEY_PREFIX + ticket)
        except redis_lib.RedisError as e:
            log.warning('티켓 조회 실패: %s', type(e).__name__)
            return None
        if raw is None:
            return None                     # 없거나 만료됐거나 이미 쓰인 티켓

        try:
            text = raw.decode() if isinstance(raw, bytes) else raw
        except (UnicodeDecodeError, AttributeError):
            log.error('티켓 값을 디코딩할 수 없다: %r', raw)
            return None

        ticket = _parse_json(text) if text.lstrip().startswith('{') else _parse_pipe(text)
        if ticket is None:
            # Spring과 형식이 어긋난 경우다. 통과시키면 인증이 무의미하므로 거절한다.
            log.error('티켓 형식이 Spring과 다르다: %r — 양쪽 구현을 맞출 것', text)
        return ticket


def _parse_json(text: str) -> Optional[Ticket]:
    """현행 형식. {"streamerId": 42, "sessionId": 1087, "terms": {...}}"""
    try:
        data = json.loads(text)
        return Ticket(
            int(data['streamerId']),
            int(data['sessionId']),
            # 사전을 못 읽어도 접속은 막지 않는다. 기본 사전으로 방송할 수 있는 편이
            # 방송을 아예 시작하지 못하는 것보다 낫다.
            PersonalTerms.parse(data.get('terms')),
        )
    except (json.JSONDecodeError, KeyError, TypeError, ValueError):
        return None


def _parse_pipe(text: str) -> Optional[Ticket]:
    """구형식. "streamerId|sessionId"

    Spring이 JSON으로 넘어가기 전에 발급한 티켓을 위한 전환기 경로다. 배포 순서에 따라
    한쪽이 먼저 올라가면 이게 없는 동안 모든 음성 접속이 조용히 거절된다.
    양쪽 배포가 끝나면 이 함수와 TICKET_DELIMITER를 지운다.
    """
    try:
        streamer_id, session_id = text.split(TICKET_DELIMITER, 1)
        return Ticket(int(streamer_id), int(session_id))
    except (ValueError, AttributeError):
        return None

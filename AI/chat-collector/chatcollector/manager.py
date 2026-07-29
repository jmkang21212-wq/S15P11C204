"""세션별 수집 워커 레지스트리."""
import logging
import threading
from typing import Optional

from chatcollector.session import CollectorSession
from chatcollector.spring import SpringClient

log = logging.getLogger(__name__)


class CollectorManager:
    """sessionId 하나당 워커 하나를 유지한다. 시작·종료는 멱등하다."""

    def __init__(self, spring: Optional[SpringClient] = None):
        self._spring = spring or SpringClient()
        self._sessions: dict[int, CollectorSession] = {}
        self._lock = threading.Lock()

    def start(self, session_id: int) -> bool:
        """새로 시작했으면 True, 이미 돌고 있었으면 False."""
        with self._lock:
            if session_id in self._sessions:
                return False
            session = CollectorSession(session_id, self._spring)
            self._sessions[session_id] = session

        # Lock 밖에서 스레드를 띄운다. start()가 Spring을 호출할 수 있어
        # Lock을 쥔 채로 부르면 다른 세션의 시작·종료가 함께 막힌다.
        session.start()
        return True

    def stop(self, session_id: int, grace_period_sec: float) -> bool:
        """멈췄으면 True, 원래 돌고 있지 않았으면 False."""
        with self._lock:
            session = self._sessions.pop(session_id, None)
        if session is None:
            return False
        session.stop(grace_period_sec)
        return True

    def snapshot(self, session_id: int) -> Optional[dict]:
        with self._lock:
            session = self._sessions.get(session_id)
        return None if session is None else session.snapshot()

    def snapshots(self) -> list[dict]:
        with self._lock:
            sessions = list(self._sessions.values())
        return [session.snapshot() for session in sessions]

    def shutdown(self) -> None:
        """프로세스 종료 시 모든 세션을 정리한다."""
        with self._lock:
            sessions = list(self._sessions.values())
            self._sessions.clear()
        for session in sessions:
            try:
                session.stop()
            except Exception as exception:  # noqa: BLE001
                log.warning('세션 정리 실패: sessionId=%s %s',
                            session.session_id, exception)
        self._spring.close()

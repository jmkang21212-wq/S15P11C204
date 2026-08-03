"""치지직 채팅 수집기 제어 서버 (FastAPI).

Spring의 ChatCollectorGateway가 방송 준비·종료 시점에 호출한다.

실행:
    uvicorn server:app --host 0.0.0.0 --port 8002

문서:
    http://localhost:8002/docs
    계약: README.md
"""
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Response

from chatcollector.config import CONTROL_TOKEN
from chatcollector.manager import CollectorManager

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)s [%(name)s] %(message)s',
)
log = logging.getLogger('chatcollector')

manager = CollectorManager()


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield
    # 프로세스가 내려갈 때 남은 채팅을 Flush한다.
    manager.shutdown()


app = FastAPI(
    title='치지직 채팅 수집기',
    version='1.0',
    description='세션별 socket.io 채팅 수집과 시청자 수 폴링',
    lifespan=lifespan,
)


def require_token(authorization: str | None = Header(default=None)):
    """COLLECTOR_CONTROL_TOKEN이 설정된 경우에만 Bearer 검사. 비어 있으면 통과(로컬 개발)."""
    if not CONTROL_TOKEN:
        return
    if authorization != f'Bearer {CONTROL_TOKEN}':
        raise HTTPException(status_code=401, detail='invalid token')


@app.post('/collectors/{session_id}', status_code=202,
          dependencies=[Depends(require_token)])
def start_collector(session_id: int):
    """세션 수집을 시작한다. 이미 돌고 있으면 아무것도 하지 않는다.

    Spring의 준비 Outbox 이벤트는 재시도되므로 멱등해야 한다.
    """
    started = manager.start(session_id)
    return {'sessionId': session_id, 'started': started}


@app.post('/collectors/platform-sessions/{platform_session_id}', status_code=202,
          dependencies=[Depends(require_token)])
def start_platform_collector(platform_session_id: int):
    started = manager.start_platform(platform_session_id)
    return {'platformSessionId': platform_session_id, 'started': started}


@app.delete('/collectors/{session_id}', dependencies=[Depends(require_token)])
def stop_collector(
    session_id: int,
    gracePeriodMs: int = Query(default=30_000, ge=0, le=300_000),
):
    """세션 수집을 종료하고 남은 채팅을 Flush한다."""
    stopped = manager.stop(session_id, gracePeriodMs / 1000.0)
    return {'sessionId': session_id, 'stopped': stopped}


@app.delete('/collectors/platform-sessions/{platform_session_id}',
            dependencies=[Depends(require_token)])
def stop_platform_collector(
    platform_session_id: int,
    gracePeriodMs: int = Query(default=30_000, ge=0, le=300_000),
):
    stopped = manager.stop(platform_session_id, gracePeriodMs / 1000.0)
    return {'platformSessionId': platform_session_id, 'stopped': stopped}


@app.get('/collectors', dependencies=[Depends(require_token)])
def list_collectors():
    return {'sessions': manager.snapshots()}


@app.get('/collectors/{session_id}', dependencies=[Depends(require_token)])
def get_collector(session_id: int):
    snapshot = manager.snapshot(session_id)
    if snapshot is None:
        raise HTTPException(status_code=404, detail='not collecting')
    return snapshot


@app.get('/health')
def health():
    return Response(status_code=204)

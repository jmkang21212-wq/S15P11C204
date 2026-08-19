"""부하 실험용 래퍼. 서버 코드를 한 줄도 고치지 않고 스레드풀 크기만 바꾼다.

  uvicorn threadpool_patch:app --app-dir chat-ai

server.py 의 app 을 그대로 가져다 쓰고, 기동 시점에 anyio 스레드풀
상한만 덮어쓴다. 추론·Redis·라우팅 로직은 손대지 않는다.
이 파일은 이미지에 없고 부하 테스트 때만 읽기 전용으로 마운트된다.

왜 필요한가
-----------
/ingest 는 동기 함수(def)라 FastAPI 가 anyio 스레드풀로 넘긴다.
기본값이 40인데 컨테이너 CPU 는 2코어다. 받아들이는 양이 처리 능력의
20배라, 40개 스레드가 2코어를 두고 경합하며 서로를 방해한다.

  실측  한산할 때  64건 317ms (건당 5ms)
        부하 중     4건 996ms (건당 249ms)   <- 50배 악화
        Redis 도 3ms -> 104ms (35배)

스레드를 코어 수에 맞춰 줄이면 경합이 사라지고, 초과분은 서버 안이
아니라 큐에서 기다린다. 큐에서 기다리는 요청은 자원을 쓰지 않는다.

⚠ total_tokens 는 이벤트 루프마다 따로 있는 값(RunVar)이라 import 시점에
   설정할 수 없다. 루프가 도는 lifespan 안에서 덮어써야 한다.
   또 server.py 가 lifespan= 을 넘겨 앱을 만들었으므로 @app.on_event 는
   실행되지 않는다 - 기존 lifespan 을 감싸는 방식이어야 한다.
"""
import os
from contextlib import asynccontextmanager

import anyio.to_thread

from server import app                      # chat-ai/server.py 의 그 app

THREADS = int(os.getenv('BENCH_THREADPOOL', '4'))

_original = app.router.lifespan_context


@asynccontextmanager
async def _lifespan(a):
    limiter = anyio.to_thread.current_default_thread_limiter()
    before = limiter.total_tokens
    limiter.total_tokens = THREADS
    print(f'[bench] 스레드풀 {before} -> {THREADS}', flush=True)
    async with _original(a):
        yield


app.router.lifespan_context = _lifespan

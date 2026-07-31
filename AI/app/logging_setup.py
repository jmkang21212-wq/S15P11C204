"""
로깅 설정 — 터미널과 파일에 동시에 남긴다.

`chat-ai/chatai/logging_setup.py`와 같은 내용이다. 두 서버가 별도 패키지 루트라
공유 import가 어려워 복제했다. **한쪽을 고치면 다른 쪽도 맞출 것.**

왜 필요한가: uvicorn의 기본 로깅 설정은 `uvicorn`·`uvicorn.access` 로거만 건드리고
**root 로거는 그대로 둔다.** 그래서 이 설정이 없으면 `log.info(...)`가 핸들러 없이
사라지고, WARNING 이상만 포맷 없는 stderr로 겨우 나간다.
"""
import logging
import os
from logging.handlers import RotatingFileHandler

FORMAT = '%(asctime)s %(levelname)-5s [%(name)s] %(message)s'
DATEFMT = '%Y-%m-%d %H:%M:%S'

MAX_BYTES = 10 * 1024 * 1024      # 파일 하나 10MB
BACKUP_COUNT = 5                  # 총 50MB까지 보관 후 오래된 것부터 삭제


def setup_logging(name=__name__, log_file=None, level=None):
    """root 로거에 콘솔·파일 핸들러를 붙인다. 서버 기동 시 1회만 호출."""
    level = (level or os.getenv('LOG_LEVEL', 'INFO')).upper()
    fmt = logging.Formatter(FORMAT, datefmt=DATEFMT)

    root = logging.getLogger()
    root.setLevel(level)
    for h in list(root.handlers):     # 재호출 시 중복 출력 방지
        root.removeHandler(h)

    console = logging.StreamHandler()
    console.setFormatter(fmt)
    root.addHandler(console)

    if log_file:
        os.makedirs(os.path.dirname(os.path.abspath(log_file)), exist_ok=True)
        fh = RotatingFileHandler(log_file, maxBytes=MAX_BYTES,
                                 backupCount=BACKUP_COUNT, encoding='utf-8')
        fh.setFormatter(fmt)
        root.addHandler(fh)

    # uvicorn은 자체 핸들러 + propagate=False라 우리 파일에 안 남는다.
    # 전파시켜 접근 로그까지 같은 파일에 시간순으로 모이게 한다.
    for uv in ('uvicorn', 'uvicorn.error', 'uvicorn.access'):
        lg = logging.getLogger(uv)
        lg.handlers.clear()
        lg.propagate = True

    return logging.getLogger(name)

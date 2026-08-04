"""
로깅 설정 — 터미널과 파일에 동시에 남긴다.

왜 필요한가: uvicorn의 기본 로깅 설정은 `uvicorn`·`uvicorn.access` 로거만 건드리고
**root 로거는 그대로 둔다.** 그래서 `logging.basicConfig()` 같은 설정이 없으면
우리 코드의 `log.info(...)`는 핸들러가 없어 전부 사라지고, WARNING 이상만
포맷 없는 stderr(`logging.lastResort`)로 겨우 나간다.

호출 비용은 무시해도 된다 — 실측 파일 기록 18μs/건으로 ONNX 추론(3,100μs)의 0.6%다.
반대로 `log.debug(...)`는 레벨 미달이면 0.11μs라 사실상 공짜이므로,
상세 로그는 DEBUG로 심어두고 필요할 때만 켜면 된다.

사용:
    from chatai.logging_setup import setup_logging
    setup_logging('chatai', 'logs/chatai.log')
"""
import logging
import os
from logging.handlers import RotatingFileHandler

FORMAT = '%(asctime)s %(levelname)-5s [%(name)s] %(message)s'
DATEFMT = '%Y-%m-%d %H:%M:%S'

MAX_BYTES = 10 * 1024 * 1024      # 파일 하나 10MB
BACKUP_COUNT = 5                  # 총 50MB까지 보관 후 오래된 것부터 삭제


def setup_logging(name='chatai', log_file=None, level=None):
    """root 로거에 콘솔·파일 핸들러를 붙인다. 서버 기동 시 1회만 호출.

    log_file: None이면 콘솔만. 상대경로면 프로세스 작업 디렉터리 기준.
    level   : None이면 환경변수 LOG_LEVEL, 그것도 없으면 INFO.
    """
    level = (level or os.getenv('LOG_LEVEL', 'INFO')).upper()
    fmt = logging.Formatter(FORMAT, datefmt=DATEFMT)

    root = logging.getLogger()
    root.setLevel(level)
    # 재호출(테스트·리로드) 시 핸들러가 쌓여 같은 줄이 여러 번 찍히는 것을 막는다
    for h in list(root.handlers):
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

    # uvicorn은 자체 핸들러를 쓰고 propagate=False라 우리 파일에 안 남는다.
    # 핸들러를 떼고 전파시켜, 접근 로그까지 같은 파일에 시간순으로 모이게 한다.
    for uv in ('uvicorn', 'uvicorn.error', 'uvicorn.access'):
        lg = logging.getLogger(uv)
        lg.handlers.clear()
        lg.propagate = True

    return logging.getLogger(name)

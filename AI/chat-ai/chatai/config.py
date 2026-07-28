"""chat-ai 설정 — 전부 환경변수로 덮어쓸 수 있다."""
import os

# 슬라이딩 윈도우 길이(초). BACKEND_API.md의 windowSec과 같은 값이어야 한다.
WINDOW_SEC = int(os.getenv('CHATAI_WINDOW_SEC', '180'))

# 팀 Redis(BackEnd/spring/laive/compose.yaml)를 공유한다.
# Spring이 DB 0을 쓰므로 chat-ai는 DB 1 — 키 충돌이 원천적으로 없다.
REDIS_URL = os.getenv('CHATAI_REDIS_URL', 'redis://127.0.0.1:6379/1')

# 서버 간 인증 토큰. 비워두면 검사하지 않는다 (로컬 개발용).
CHATAI_TOKEN = os.getenv('CHATAI_TOKEN', '')

# Redis 키 TTL(초) — 방송 종료 후 DELETE를 못 받아도 스스로 사라지는 안전망.
UID_TTL = 600       # 유니크 채터 ZSET · viewers: 윈도우(180s)보다 넉넉히
BUCKET_TTL = 300    # 초단위 감정 버킷: 윈도우 + 여유

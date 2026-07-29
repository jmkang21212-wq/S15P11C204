"""chat-ai 설정 — 전부 환경변수로 덮어쓸 수 있다."""
import os
from pathlib import Path

from dotenv import load_dotenv

# AI/.env 를 공유한다 (GMS_KEY 등 — 음성 서비스와 같은 파일).
# chatai/ -> chat-ai/ -> AI/ 이므로 두 단계 위.
load_dotenv(Path(__file__).resolve().parents[2] / '.env')

# 슬라이딩 윈도우 길이(초). CHAT_AI_API.md의 windowSec과 같은 값이어야 한다.
WINDOW_SEC = int(os.getenv('CHATAI_WINDOW_SEC', '180'))

# 팀 Redis(BackEnd/spring/laive/compose.yaml)를 공유한다.
# Spring이 DB 0을 쓰므로 chat-ai는 DB 1 — 키 충돌이 원천적으로 없다.
REDIS_URL = os.getenv('CHATAI_REDIS_URL', 'redis://127.0.0.1:6379/1')

# 서버 간 인증 토큰. 비워두면 검사하지 않는다 (로컬 개발용).
CHATAI_TOKEN = os.getenv('CHATAI_TOKEN', '')

# Redis 키 TTL(초) — 방송 종료 후 DELETE를 못 받아도 스스로 사라지는 안전망.
UID_TTL = 600       # 유니크 채터 ZSET · viewers · 원문 LIST: 윈도우(180s)보다 넉넉히
BUCKET_TTL = 300    # 초단위 감정 버킷: 윈도우 + 여유

# --- LLM 여론 요약 (/summary) ---
# GMS: SSAFY 제공 OpenAI 호환 프록시. 음성 서비스(app/config.py)와 동일 설정.
GMS_KEY = os.getenv('GMS_KEY', '')
GMS_BASE_URL = 'https://gms.ssafy.io/gmsapi/api.openai.com/v1'
SUMMARY_MODEL = 'gpt-5-nano'      # GMS 사용량 제한이 mini보다 여유로움 (품질 비교: 2026-07-29)
SUMMARY_REASONING = 'low'         # nano는 minimal에서 지시 이해가 흔들림 — low로 보정

SUMMARY_MIN_CHATS = int(os.getenv('CHATAI_SUMMARY_MIN_CHATS', '30'))    # 미만이면 422
SUMMARY_COOLDOWN_SEC = int(os.getenv('CHATAI_SUMMARY_COOLDOWN', '60'))  # 이내 재요청 409
SUMMARY_SAMPLE_MAX = 300          # LLM 입력 채팅 상한

# 이 비율 이상인 감정만 텍스트 샘플링 (중립 제외).
# ⚠ 프론트의 감정 칩 표시 임계와 같은 값이어야 한다 — 변경 시 CHAT_AI_API.md와
#    프론트 상수를 함께 수정할 것.
SUMMARY_FOCUS_THRESH = float(os.getenv('CHATAI_SUMMARY_FOCUS_THRESH', '0.10'))

# 중립 맥락(상황 중계) 슬롯. 0이면 끔. 전사 연계가 없는 현재 "무엇이 벌어지는지"의
# 주 공급원이 중립 상황 중계라 기본 켜둔다.
SUMMARY_NEUTRAL_QUOTA = int(os.getenv('CHATAI_SUMMARY_NEUTRAL_QUOTA', '30'))

# 방송 환경 문제(소리·화면 등) 채팅이 윈도우에 이 건수 이상일 때만 마무리 안내를
# 붙인다. 판정은 LLM이 아니라 코드(키워드 매칭 수)가 한다 — 결정론적 게이트.
SUMMARY_ENV_MIN = int(os.getenv('CHATAI_SUMMARY_ENV_MIN', '5'))

MSG_KEEP = 3000                   # 방송당 원문 보관 상한 (LTRIM)

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

# --- 반복 질문 Top N (/questions) ---
# 임베딩 모델. 3-small은 쓸 수 없다 — 실측에서 같은 질문("몇시까지 하세요"~
# "언제 끝나요")이 0.225로 나와 다른 질문("내일도 방송해요", 0.330)보다 낮았다.
# 3-large는 같은 질문 0.47~0.91 / 다른 질문 0.13~0.25로 분리된다.
EMBED_MODEL = os.getenv('CHATAI_EMBED_MODEL', 'text-embedding-3-large')

# 같은 질문 판정 임계. 실측(VOD 질문 900건, 3-large)으로 확정했다.
#
# 온라인 클러스터링은 중심점을 갱신하므로 A~B, B~C인데 A≁C인 것들이 사슬처럼
# 이어붙는 드리프트가 생긴다. 임계가 낮을수록 심하다:
#   0.45  1위 묶음 = '또 울었어?' + '2프로만?' + '그런게 가능해?'  ← 오염
#   0.50  1위 묶음 = '또 울었어?' + '진짜 울어?' + '이번엔 안 울고 있지?'
#                  + '이러다가 또 우는 거 아니지?'                ← 전부 같은 주제
#
# 중심점 '고정'(갱신 안 함)도 시험했다. 드리프트는 막지만 316~416개로 과분할돼
# 표현 변형을 못 흡수한다 — 갱신은 유지하고 임계로 잡는 쪽이 낫다.
#
# 표본 9쌍으로 정했던 초기값 0.38은 틀렸다. 실제 채팅은 문장이 짧고 어미가
# 비슷해 유사도가 전반적으로 높게 나온다.
Q_SIM_THRESHOLD = float(os.getenv('CHATAI_Q_SIM', '0.50'))

# 리액션 필터를 통과해도 너무 짧으면 답할 거리가 아니다.
# 실측: is_reaction만으로는 '난가?'(69회) '어라?'(68회) '실수?'(58회)가 남아
# 상위를 오염시킨다 — 완성형 한글이라 자모 필터에 안 걸리기 때문. 4자로 사라진다.
Q_MIN_CONTENT_LEN = int(os.getenv('CHATAI_Q_MIN_LEN', '4'))

Q_BATCH_SIZE = 100                # 이만큼 모이면 임베딩
Q_BATCH_SEC = 30                  # 덜 모여도 이 시간이면 임베딩
Q_MAX_CLUSTERS = 2000             # 초과 시 askers 최소부터 제거
Q_MIN_ASKERS = int(os.getenv('CHATAI_Q_MIN_ASKERS', '3'))   # Top N 노출 최소 인원
Q_PENDING_MAX = 5000              # GMS 장애 시 버틸 대기열 상한
Q_TTL = 43200                     # 12시간 — 장방송 대비 (윈도우 10분과 다르다)
Q_LOCK_SEC = 60                   # 워커 싱글톤 락 (다중 uvicorn 워커 대비)

# FAQ 판정 — "스트리머가 맥락 없이 답할 수 있는 질문인가"
# 규칙·임베딩으로는 안 된다(실측):
#   의문사 필터: '진짜 뭐함?'(15회) '무슨 실수?'(6회)가 통과 — 양만 줄고 종류를 못 가름
#   앵커 유사도: '이 게임을 하라는거냐?'가 0.636으로 1위 — 주제만 같으면 붙는다
# 답변 가능성은 의미 판단이라 LLM이 필요하다. 다만 개별 채팅이 아니라
# **클러스터 대표 문장만** 판정하므로 방송당 수백 건이고, 캐시하면 1회씩만 부른다.
Q_FAQ_JUDGE_MAX = 50              # 배치당 판정 상한 (비용 상한)

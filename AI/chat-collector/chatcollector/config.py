"""치지직 채팅 수집기 설정 — 전부 환경변수로 덮어쓸 수 있다.

값은 `AI/.env`에 둔다(app/config.py와 같은 방식). `.env.example`을 복사해 채운다.

shell의 export로만 넘기면 터미널을 닫는 순간 사라진다. VIEWER_HASH_SALT는 한번
정하면 바뀌어서는 안 되는 값이라 반드시 파일에 있어야 한다.
"""
import os

from dotenv import load_dotenv

# 인자 없이 부르면 호출한 파일 위치에서 상위로 올라가며 .env를 찾는다.
# chat-collector/ 에서 실행해도 AI/.env가 잡힌다.
load_dotenv()

# --- 제어 서버 ---

# Spring이 수집기를 시작·종료할 때 보내는 Bearer 토큰. 비워두면 검사하지 않는다(로컬 개발용).
CONTROL_TOKEN = os.getenv('COLLECTOR_CONTROL_TOKEN', '')

# --- Spring ---

SPRING_BASE_URL = os.getenv('SPRING_BASE_URL', 'http://127.0.0.1:8080')

# Spring의 laive.internal.service-token과 같은 값이어야 한다(API 명세 13.1).
SPRING_TOKEN = os.getenv('SPRING_TOKEN', '')

SPRING_TIMEOUT = float(os.getenv('COLLECTOR_SPRING_TIMEOUT', '5'))
SPRING_MAX_RETRIES = int(os.getenv('COLLECTOR_SPRING_MAX_RETRIES', '3'))

# --- 치지직 ---

CHZZK_API_BASE = os.getenv('CHZZK_API_BASE', 'https://openapi.chzzk.naver.com')
YOUTUBE_API_BASE = os.getenv('YOUTUBE_API_BASE', 'https://www.googleapis.com')

# 시청자 수 폴링. Open API에 채널별 동시 시청자 수가 없어 서비스 API를 쓴다.
CHZZK_LIVE_STATUS_URL = os.getenv(
    'CHZZK_LIVE_STATUS_URL',
    'https://api.chzzk.naver.com/polling/v2/channels/{channel_id}/live-status',
)

# --- 채팅 배치 ---

# 채팅을 모아 보내는 주기(초)와 최대 건수. 명세 13.6의 1초 배치와 맞춘다.
CHAT_BATCH_SEC = float(os.getenv('COLLECTOR_CHAT_BATCH_SEC', '1.0'))
CHAT_BATCH_SIZE = int(os.getenv('COLLECTOR_CHAT_BATCH_SIZE', '100'))

# Spring이 거부하는 상한(laive.internal.max-batch-items)보다 반드시 작아야 한다.
CHAT_QUEUE_MAX = int(os.getenv('COLLECTOR_CHAT_QUEUE_MAX', '10000'))

# --- 시청자 수 폴링 ---

VIEWER_POLL_SEC = float(os.getenv('COLLECTOR_VIEWER_POLL_SEC', '10'))

# HIGHLIGHT_SPEC.md 가드 4의 "마지막 정상값 최대 5분". 수집기는 이 값을 직접
# 적용하지 않고(합성 측정치를 저장하지 않는다) 상태 보고에 마지막 정상값의
# 나이를 실어 판정 계층이 같은 기준을 쓰게 한다.
VIEWER_STALE_AFTER_SEC = float(os.getenv('COLLECTOR_VIEWER_STALE_AFTER_SEC', '300'))

# --- socket.io 하트비트 ---

# 우리가 engine.io PING을 보내는 주기(초).
#
# 치지직 서버는 핸드셰이크에서 pingInterval 25초를 광고하고 **PING을 한 번도
# 보내지 않는다**(2026-07-29 실측). EIO v4에서 ping은 서버 몫이라 클라이언트는
# 보낼 PONG이 없고, 그러면 python-engineio가 두 곳에서 연결을 포기한다.
#
#   쓰기 루프: max(pingInterval, pingTimeout) + 5 = 65초 동안 보낼 게 없으면 중단
#   읽기 루프: pingInterval + pingTimeout = 85초 동안 수신이 없으면 중단
#
# 실측으로 정확히 이 값에 끊겼다(채팅 없으면 85초, 39초에 채팅이 있으면 124초).
# 우리가 PING을 보내면 서버가 PONG으로 답하므로 두 조건이 동시에 충족된다.
# 두 한계보다 충분히 짧아야 한다.
SOCKET_PING_SEC = float(os.getenv('COLLECTOR_SOCKET_PING_SEC', '20'))

# --- 재핸드셰이크 ---

# 치지직 socket.io는 자동 재연결이 불가능하다. 끊기면 전체 핸드셰이크를 다시 하며
# 이 백오프로 간격을 늘린다.
RECONNECT_BACKOFF_START_SEC = float(os.getenv('COLLECTOR_RECONNECT_BACKOFF_START', '1'))
RECONNECT_BACKOFF_MAX_SEC = float(os.getenv('COLLECTOR_RECONNECT_BACKOFF_MAX', '30'))

# --- 상태 보고 ---

# Spring의 laive.collector.heartbeat-timeout보다 충분히 짧아야 한다.
# 기본값은 Spring 30초 / 수집기 10초로 세 번 유실까지 견딘다.
HEARTBEAT_SEC = float(os.getenv('COLLECTOR_HEARTBEAT_SEC', '10'))

# --- 시청자 해시 ---

# senderChannelId를 해시할 때 앞에 붙이는 고정 비밀값.
#
# 치지직 채널 ID는 32자리 16진수라 소금 없이 SHA-256만 걸면 전수 대조로 원본을
# 되찾을 수 있다. 가명화를 실제로 성립시키려면 이 값이 필요하다.
#
# 주의: 한번 정하면 절대 바꾸지 말 것. 바꾸는 순간 같은 시청자가 다른 해시로
# 보여 viewer_profiles에 중복 인격이 생기고 과거 데이터와 이어지지 않는다.
VIEWER_HASH_SALT = os.getenv('COLLECTOR_VIEWER_HASH_SALT', '')

# --- 시계 ---

# messageTime은 클라이언트 시계라 신뢰할 수 없다(HIGHLIGHT_SPEC.md 가드 8).
# 서버 수신 시각과 이만큼 넘게 벌어지면 수신 시각으로 대체한다. 그대로 두면
# 조작되거나 어긋난 시계 하나가 chat_messages의 먼 과거·미래 청크에 행을 만들고
# 하이라이트 채팅 리플레이의 정렬을 깨뜨린다.
MESSAGE_TIME_MAX_SKEW_SEC = float(os.getenv('COLLECTOR_MESSAGE_TIME_MAX_SKEW', '300'))

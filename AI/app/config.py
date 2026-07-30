import os

from dotenv import load_dotenv

load_dotenv()

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
if not GROQ_API_KEY:
    raise RuntimeError(".env에 GROQ_API_KEY가 없습니다. .env.example을 복사해서 채우세요.")

# SSAFY GMS. OpenAI 호환 프록시라 openai SDK에 base_url만 바꿔 끼우면 된다.
GMS_KEY = os.getenv("GMS_KEY")
GMS_BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1"
LLM_MODEL = "gpt-5-mini"

LLM_REASONING_EFFORT = "minimal"
LLM_WORKERS = 2
LLM_QUEUE_MAX = 16      
LLM_MAX_RETRIES = 1
LLM_CONTEXT_SEC = 10.0
LLM_MIN_TEXT_LEN = 2

# 이 등급 이상만 위험 발언으로 알린다.
#
# 등급은 '위험한지'가 아니라 '얼마나 심한지'를 뜻한다. 위험이 아니라는 판단은
# none이 받고, low부터는 실제로 비속어가 있다는 뜻이다(프롬프트에 그렇게 명시했다).
# 그래서 none만 걸러내면 된다.
#
# 이 구분이 없던 시절에는 LLM이 low를 "위험도 낮음(= 위험 아님)"으로 쓰는 바람에
# 평범한 대화에 경고가 붙었다. category=none 방어와 함께 봐야 한다.
LLM_ALERT_MIN_RISK = "low"

# --- 오디오 ---
SAMPLE_RATE = 16000
CHUNK = 512        

# --- STT ---
STT_MODEL = "whisper-large-v3"
STT_LANGUAGE = "ko"

STT_WORKERS = 2      
STT_QUEUE_MAX = 32     
STT_MAX_RETRIES = 2

# 환각 필터. Whisper는 무음/잡음에 "감사합니다" 같은 문장을 지어낸다.
STT_FILTER_ENABLED = True
NO_SPEECH_MAX = 0.6         
AVG_LOGPROB_MIN = -1.8       
HALLUCINATION_NS_MIN = 0.35  

# --- VAD / 발화 분할 ---
VAD_THRESHOLD = 0.5
SILENCE_MS = 500
MIN_SPEECH_MS = 250
MAX_UTTERANCE_MS = 6000
PRE_ROLL_MS = 300

# --- 약속 추적 ---
# 위험 감지와 반대로 LLM이 주력이다. "2주 안으로 공포게임 할게요"는 사전으로 못 잡는다.
# 대신 모든 발화를 LLM에 보내면 레이트리밋이 터지므로, 값싼 어미 게이트로 후보만 추린다.
PROMISE_ENABLED = True
PROMISE_WORKERS = 1        # 실시간성이 덜 중요해 1개면 충분하다
PROMISE_QUEUE_MAX = 32
PROMISE_MIN_CONFIDENCE = 0.6   # 이보다 낮으면 버린다
PROMISE_DEDUP_SEC = 900        # 같은 약속을 15분 내에 반복하면 한 번만 보낸다

# --- Spring 전송 ---
# 비어 있으면 전송을 아예 하지 않는다. 스펙 합의 전까지는 이대로 두면 된다.
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "").rstrip("/")
SPRING_TOKEN = os.getenv("SPRING_TOKEN", "")   # 서버간 인증용
SPRING_TRANSCRIPT_PATH = "/api/ai/transcripts"
SPRING_ALERT_PATH = "/api/ai/alerts"
SPRING_PROMISE_PATH = "/api/ai/promises"

# 전사는 양이 많고 지연이 상관없어 묶어 보낸다. 경고는 즉시 개별 전송한다.
SPRING_BATCH_SIZE = 10    # 이만큼 모이면 보낸다
SPRING_BATCH_SEC = 2.0    # 덜 모여도 이 시간이 지나면 보낸다
SPRING_TIMEOUT = 5.0
SPRING_MAX_RETRIES = 3
SPRING_QUEUE_MAX = 500    # Spring이 죽어 있는 동안 버틸 양

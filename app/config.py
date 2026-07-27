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

# --- Spring 전송 ---
# 비어 있으면 전송을 아예 하지 않는다. 스펙 합의 전까지는 이대로 두면 된다.
SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "").rstrip("/")
SPRING_TOKEN = os.getenv("SPRING_TOKEN", "")   # 서버간 인증용
SPRING_TRANSCRIPT_PATH = "/api/ai/transcripts"
SPRING_ALERT_PATH = "/api/ai/alerts"

# 전사는 양이 많고 지연이 상관없어 묶어 보낸다. 경고는 즉시 개별 전송한다.
SPRING_BATCH_SIZE = 10    # 이만큼 모이면 보낸다
SPRING_BATCH_SEC = 2.0    # 덜 모여도 이 시간이 지나면 보낸다
SPRING_TIMEOUT = 5.0
SPRING_MAX_RETRIES = 3
SPRING_QUEUE_MAX = 500    # Spring이 죽어 있는 동안 버틸 양

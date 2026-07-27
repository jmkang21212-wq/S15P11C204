# AI 서버 — 실시간 위험 발언 감지

방송 중 스트리머의 발화를 실시간으로 감시해 위험 발언을 감지한다.

```
브라우저 마이크 ──WebSocket(PCM)──> FastAPI
                                      │
                              VAD (발화 구간만 추출)
                                      │
                            Groq Whisper (전사)
                                      │
                    ┌─────────────────┴─────────────────┐
             Aho-Corasick 사전매칭              LLM 문맥 판정
             (명백한 비속어, 0.4초)          (회색지대, 2.2초)
                    └─────────────────┬─────────────────┘
                                      │
                    경고 ──> 클라이언트 (WebSocket, 즉시)
                    기록 ──> Spring (HTTP, 비동기)
```

연동 규격은 별도 문서에 있다.

- 프론트엔드: [FRONTEND_API.md](FRONTEND_API.md)
- 백엔드: [BACKEND_API.md](BACKEND_API.md)

---

## 요구 사항

| | 버전 |
|---|---|
| Python | **3.12 이상** |
| 패키지 관리 | [uv](https://docs.astral.sh/uv/) 권장 (pip도 가능) |

외부 API 키가 두 개 필요하다.

| 키 | 용도 | 발급처 |
|---|---|---|
| `GROQ_API_KEY` | 음성 인식 (Whisper) | [console.groq.com](https://console.groq.com) |
| `GMS_KEY` | LLM 판정 (gpt-5-mini) | SSAFY GMS |

## 설치

**uv 사용 시 (권장)**

```bash
cd AI
uv sync
```

`uv.lock`에 고정된 버전 그대로 설치되므로 팀원 간 환경이 정확히 일치한다.

**pip 사용 시**

```bash
cd AI
python -m venv .venv
.venv\Scripts\activate        # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
```

> `silero-vad`가 `torch`를 함께 설치하는데 약 500MB다. 최초 설치에 시간이 걸린다.
> Linux 서버에 올릴 때는 기본적으로 CUDA 버전을 받으므로, CPU만 쓸 거라면
> `pip install torch --index-url https://download.pytorch.org/whl/cpu`로 먼저 설치하는 게 좋다.

## 환경 변수

`.env.example`을 복사해서 `.env`를 만들고 키를 채운다.

```bash
copy .env.example .env        # macOS/Linux: cp .env.example .env
```

```
GROQ_API_KEY=gsk_...
GMS_KEY=...
SPRING_BASE_URL=              # 비워두면 Spring 전송을 하지 않는다
SPRING_TOKEN=
```

`SPRING_BASE_URL`이 비어 있어도 **감지 기능은 정상 동작한다.** 저장만 되지 않는다.

## 실행

```bash
cd AI
uv run uvicorn app.server:app --reload --port 8000
```

브라우저에서 **http://localhost:8000** 을 열면 테스트 클라이언트가 뜬다. "시작"을 누르고 말하면 전사와 경고를 바로 확인할 수 있다.

상태 확인:

```bash
curl http://localhost:8000/health
```

```json
{ "status": "ok", "dictionary": 38, "llm": true, "spring": false }
```

## 구조

```
AI/
├── app/
│   ├── config.py              모든 설정값 (임계값 근거를 주석에 기록)
│   ├── server.py              FastAPI + WebSocket
│   ├── audio/segmenter.py     VAD 기반 발화 분할
│   ├── stt/groq_stt.py        Groq Whisper 전사 + 환각 필터
│   ├── detect/
│   │   ├── normalize.py       사전매칭 전 텍스트 정규화
│   │   ├── dictionary.py      Aho-Corasick 매칭
│   │   ├── terms.yaml         위험 단어 사전
│   │   └── llm_judge.py       LLM 문맥 판정 + 10초 롤링 버퍼
│   └── sink/spring.py         Spring 전송 (전사 배치 / 경고 즉시)
└── static/client.html         테스트 클라이언트 (프론트 참조용)
```

## 설정 변경

임계값은 전부 [app/config.py](app/config.py)에 모여 있다. 각 값이 왜 그 숫자인지 실측 근거를 주석에 적어뒀으니 바꾸기 전에 읽어보는 것을 권한다.

자주 건드리게 되는 것:

| 값 | 의미 |
|---|---|
| `VAD_THRESHOLD` | 높이면 잡음에 덜 반응, 낮추면 작은 소리도 감지 |
| `MIN_SPEECH_MS` | 이보다 짧은 소리는 무시 (기침 등) |
| `MAX_UTTERANCE_MS` | 길게 말해도 이 시간에 끊어 전송 (실시간성 확보) |
| `STT_MODEL` | `whisper-large-v3` ↔ `whisper-large-v3-turbo` |

위험 단어는 [app/detect/terms.yaml](app/detect/terms.yaml)에 추가한다. 정규화가 공백·늘여쓰기를 흡수하므로 `"씨 발"`, `"씨이이발"` 같은 변형은 따로 넣지 않아도 된다.

## 알아둘 제약

**Groq 무료 티어는 하루 2000건이다.** 발화 1건이 요청 1건이라 활발한 방송 기준 약 2시간 분량이다. 동시 접속자가 늘면 그만큼 빨리 소진된다.

**한 사람당 하나의 연결이다.** 마이크 하나가 세션 하나이므로 화자 구분은 하지 않는다.

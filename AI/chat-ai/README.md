# chat-ai — 채팅 감정 분류 + 리플레이 시뮬레이터

FR-F-01(실시간 채팅 여론 요약) / FR-R-01(하이라이트 구간 탐지)용 AI 모듈.

두 가지가 들어 있습니다.

| | 무엇 | 누가 씁니까 |
|---|---|---|
| **추론 서버** | 채팅 한 줄 → 8감정 분류. CPU에서 동작 | Spring 백엔드가 HTTP 호출 |
| **리플레이 시뮬레이터** | 과거 방송 채팅을 원래 타이밍으로 재생하는 **가짜 치지직 서버** | 백엔드 개발·백테스트 |

---

## 1. 설치

의존성은 상위 `AI/pyproject.toml`에서 **위험 발언 감지 서버와 통합 관리**합니다.

```bash
cd AI
uv sync                        # 두 서버 의존성 한 번에 설치
cd chat-ai
python fetch_model.py          # 모델 내려받기 (최초 1회)
```

모델은 레포에 없습니다. fp32 511MB / int8 129MB라 git에 넣지 않고 별도 배포합니다.
URL 설정은 아래 [모델 배포](#5-모델-배포) 참고.

---

## 2. 추론 서버

```bash
uvicorn server:app --host 0.0.0.0 --port 8001
```

Swagger UI: <http://localhost:8001/docs> — 브라우저에서 바로 시험해볼 수 있습니다.

> **포트 8001인 이유**: 같은 `AI/` 폴더의 위험 발언 감지 서버(`app/server.py`)가
> uvicorn 기본 포트 8000을 쓰므로, 동시 실행을 위해 이 서버는 8001을 사용합니다.
> (리플레이 시뮬레이터는 8899라 충돌 없음)

### `POST /classify` — 개별 분류

```json
{"texts": ["ㅋㅋㅋ 개웃기네", "이거 왜 이래요?", "아 팀운 진짜"]}
```

```json
{
  "count": 3,
  "results": [
    {"label": "웃음/재미", "score": 0.94, "probs": {"웃음/재미": 0.94, "중립": 0.03, "...": 0.0}},
    {"label": "혼란/질문", "score": 0.91, "probs": {"...": 0.0}},
    {"label": "분노/불만", "score": 0.68, "probs": {"...": 0.0}}
  ]
}
```

입력 순서를 그대로 유지해 반환합니다. 응답 크기를 줄이려면 `"include_probs": false`.

### `POST /aggregate` — 여론 분포 (FR-F-01용)

3분 윈도우의 채팅을 통째로 넘기면 라벨별 비율을 냅니다.

```json
{"texts": ["ㅋㅋㅋ", "개웃기네", "아 뭐야", "..."]}
```

```json
{"count": 320, "distribution": {"웃음/재미": 0.41, "중립": 0.38, "...": 0.0}, "dominant": "웃음/재미"}
```

**여론 요약에는 `/classify`를 세지 말고 `/aggregate`를 쓰세요.**
`/aggregate`는 각 채팅의 확률을 그대로 합산합니다. `웃음 0.45 / 중립 0.40`처럼 애매한
채팅이 "웃음 1표"로 몰리지 않고 애매한 만큼만 반영되므로, **개별 분류를 세어 만든
분포보다 실제에 가깝습니다.** 개별 라벨이 필요한 곳(알림 트리거 등)에만 `/classify`를 쓰세요.

### `POST /broadcasts/{id}/ingest` — 실시간 방송 파이프라인 (Spring 전용)

Spring이 1초 배치로 채팅을 넘기면 건별 감정 + 슬라이딩 윈도우 지표(호응도·여론
분포)를 같은 응답에 돌려줍니다. 윈도우는 팀 Redis(DB 1)에 있어 서버를 재시작해도
지표가 이어집니다. `DELETE /broadcasts/{id}`로 정리 (안 불러도 TTL 자동 정리).

**요청/응답 스키마·운영 규칙은 [CHAT_AI_API.md](../CHAT_AI_API.md)가 정본입니다.**

### `GET /health`

모델 로드 상태와 라벨 목록, Redis 연결 여부(`redis: true|false`). 헬스체크·기동 확인용.

### 라벨 8종

`웃음/재미` · `환호/감탄` · `응원/호감` · `놀람` · `슬픔/아쉬움` · `분노/불만` · `혼란/질문` · `중립`

### 성능

| 항목 | 값 |
|---|---|
| 정확도 (골드셋 1,000건) | accuracy **0.780** / macro-F1 **0.714** |
| 속도 (fp32, 배치 32) | 약 **30ms/건** (2 vCPU 기준, 실서버는 2~3배 빠름) |
| 메모리 | 약 600MB |

GPU 불필요합니다. 처리량이 부족하면 `CHATAI_INT8=1`로 int8을 쓸 수 있습니다(2배 빠름,
정확도 macro-F1 1.9점 하락).

### 알려진 한계 — 설계 시 반영하세요

**`분노/불만`의 재현율이 0.508입니다.** 불만 채팅의 약 절반을 놓칩니다.
표면 표지(ㅋㅋ, ㅠㅠ, ?)가 없어 의미를 이해해야 하는 유일한 라벨이기 때문입니다.

→ **부정 여론 알림을 `분노/불만` 개수만으로 트리거하면 놓칩니다.**
`/aggregate`의 분포 비율 변화(예: 직전 윈도우 대비 부정 비중 급증)를 함께 보세요.

---

## 3. 리플레이 시뮬레이터

라이브 방송 없이 백엔드를 개발·검증하기 위한 **가짜 치지직 서버**입니다.
과거 방송 채팅을 원래 타이밍대로 다시 흘려보냅니다.

```bash
python replay/replay.py --list                              # 재생 가능 목록
python replay/replay.py --video 14189073                    # 실시간 재생
python replay/replay.py --video 14189073 --speed 4          # 4배속
python replay/replay.py --video 14189073 --speed 0          # 최대속 (백테스트용)
python replay/replay.py --video 14189073 --start-offset 1200  # 20분 지점부터
```

**접속 주소만 바꾸면 운영 코드를 한 줄도 안 고치고 붙습니다.**

```
wss://kr-ss1.chat.naver.com/chat  →  ws://127.0.0.1:8899/chat
시청자 수 폴링                     →  http://127.0.0.1:8899/polling/v2/channels/{채널ID}/live-status
```

재생은 **첫 클라이언트가 접속하면 시작**합니다 (`--autostart`로 즉시 시작 가능).

### 지원 프로토콜

실제 치지직과 동일합니다. `cmd` 번호로 메시지 종류를 구분합니다.

| cmd | 의미 | 방향 |
|---|---|---|
| 100 → 10100 | 입장 → sid 발급 | C→S, S→C |
| 5101 → 15101 | 최근 채팅 요청 → 직전 50건 | C→S, S→C |
| 0 → 10000 | ping → pong | S→C, C→S |
| **93101** | 채팅 (트래픽의 99%) | S→C |
| **93102** | 후원 (`extras.payAmount`에 금액) | S→C |

### 검증 상태

VOD `14189073`(2,403건)로 확인했습니다.

```
파일 전체            2,403건
  offset 이전         175건  (재생 대상 아님)
  offset 이후       2,228건  ← 재생돼야 할 건수
수신                2,228건
차이                    +0건   ✅ 누락 없음
```

후원 5건도 `93102`로 정확히 분리되고, 종료 후 `live-status`가 `CLOSE`로 바뀝니다.

접속 예제는 [replay/replay_smoke.py](replay/replay_smoke.py)를 그대로 참고하세요.
실제 수집기(`collect.py`)와 동일한 파싱 경로를 쓰므로, 이걸 통과하면 실서버 코드도 통과합니다.

```bash
python replay/replay_smoke.py --seconds 20
```

### 채팅 데이터는 별도입니다

`data/` 폴더(약 5.4GB)는 **레포에 없습니다.** uid·닉네임이 든 개인정보라 커밋 금지입니다.
시뮬레이터를 쓰려면 데이터 담당자에게 받아 `chat-ai/data/`에 두세요.

**시청자 실측이 있는 VOD**(`--list`에서 `O` 표시)를 쓰세요. 호응도(유니크 채터 ÷ 시청자 수)
기반 하이라이트 검증에는 실측이 필수입니다. 없으면 채팅 기반 합성값으로 대체되는데
알고리즘 검증에는 부적절합니다.

---

## 4. 전처리 — 건드리지 마세요

`chatai/text_norm.py`가 **유일한 정본**입니다. 다른 곳에 복사하지 말고 import 하세요.

```
1) {:이모티콘:} → 공백
2) 3개 초과 반복 → 정확히 3개  (ㅋㅋ는 유지, ㅋㅋㅋㅋㅋ → ㅋㅋㅋ)
3) 공백 정규화
   ※ 순서를 바꾸면 결과가 달라집니다
```

**추론 서버가 내부에서 처리하므로 백엔드는 원문을 그대로 넘기면 됩니다.**
밖에서 따로 전처리하지 마세요.

학습 때와 서빙 때의 전처리가 조금이라도 다르면 **성능이 조용히 떨어지고 로그에 아무
에러도 남지 않습니다.** 회귀 테스트로 방어선을 걸어뒀습니다:

```bash
python tests/test_text_norm.py
```

다른 언어(Java 등)로 재구현할 일이 생기면 검증 벡터를 뽑아 쓰세요:

```bash
python chatai/text_norm.py --export-tests
```

---

## 5. 모델 배포

모델 파일 7개는 팀 Google Drive 폴더에 있고, **파일 ID가 `fetch_model.py`에 박혀
있습니다.** 받는 쪽은 아무 설정 없이 실행만 하면 됩니다:

```bash
python fetch_model.py
```

이미 받은 파일(크기 일치)은 건너뛰므로 재실행해도 안전합니다.

### 모델 갱신 시 (올리는 쪽)

1. Colab 산출물(`model.onnx`, `model.int8.onnx`, `tokenizer.json`, `vocab.txt`,
   `config.json`, `tokenizer_config.json`, `special_tokens_map.json`)을
   팀 Drive 폴더에 업로드
2. 폴더 공유가 **"링크가 있는 모든 사용자(뷰어)"** 인지 확인
3. `fetch_model.py`의 `FILES`에서 각 파일의 **ID와 크기(bytes)** 를 새 값으로 교체해 커밋

크기 값은 다운로드 무결성 검증에 쓰입니다 — Drive 파일 상세에서 확인하거나,
업로드한 원본의 바이트 크기를 그대로 적으면 됩니다.

---

## 6. 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `CHATAI_MODEL_DIR` | `models/kcelectra-chat-8emo-onnx` | 모델 폴더 |
| `CHATAI_INT8` | `0` | `1`이면 int8 사용 (2배 빠름, 정확도 1.9점 하락) |
| `CHATAI_DATA_DIR` | `chat-ai/data` | 리플레이용 채팅 데이터 폴더 |

`CHATAI_DATA_DIR`은 패키지 루트 기준 절대경로로 해석되므로, **어느 디렉터리에서
실행해도 동작합니다.** 데이터를 다른 곳에 두었다면 이 변수로 지정하세요.

```bash
set CHATAI_DATA_DIR=D:\chzzk\data
python replay/replay.py --list
```

---

## 7. 구조

```
chat-ai/
├── server.py               FastAPI 추론 서버
├── fetch_model.py          모델 내려받기
├── chatai/
│   ├── classifier.py       ONNX 추론 래퍼 (predict / aggregate)
│   ├── text_norm.py        전처리 정본 ⚠️ 복사 금지
│   └── cmd_type.py         치지직 프로토콜 cmd 상수
├── replay/
│   ├── replay.py           가짜 치지직 서버
│   └── replay_smoke.py     검증 클라이언트 겸 접속 예제
├── tests/test_text_norm.py 전처리 회귀 테스트
├── models/                 (git 제외 — fetch_model.py가 채움)
└── data/                   (git 제외 — 개인정보, 별도 전달)
```

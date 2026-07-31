# AI 서버 실행 가이드 (PowerShell)

> 대상: **팀 전체**. AI 폴더에는 서버가 **3개** 있고 포트가 각각 다르다.
> 셋 다 독립적이라 필요한 것만 띄우면 된다.

| 포트 | 서버 | 무슨 일을 하나 | 언제 필요한가 |
|---|---|---|---|
| **8000** | 위험 발언 감지 (`app/`) | 프론트가 보낸 오디오 → STT → 비속어·위험 발언 판정 → Spring에 push | 음성 기능 개발·시연 |
| **8001** | 채팅 감정 분류 (`chat-ai/`) | Spring이 보낸 채팅 → 8감정 분류 + 호응도·여론 지표 + LLM 요약 | 채팅 여론 화면 개발·시연 |
| **8002** | 치지직 채팅 수집기 (`chat-collector/`) | 치지직 접속 → 채팅·시청자 수집 → Spring에 push | 실제 방송 채팅이 필요할 때 |

```
프론트 ──오디오(WS)──▶ 8000 ──▶ Spring
치지직 ──socket.io──▶ 8002 ──▶ Spring ──▶ 8001 ──▶ (감정·지표·요약)
```

**가상환경은 `AI\.venv` 하나뿐이다.** 서버가 셋이어도 같은 venv·같은 `.env`를 쓴다.

아래 예시의 `C:\projects\S15P11C204`는 **본인 저장소 경로로 바꿔서** 쓸 것.
명령은 전부 **PowerShell 기준**이다.

---

# A. 최초 1회 세팅

## A-1. 가상환경 만들기

```powershell
cd C:\projects\S15P11C204\AI
uv sync
```

이 한 줄이 `.venv` 생성 + 의존성 설치를 모두 한다. Python 3.12도 없으면 uv가 받아온다.

`uv`가 없으면 먼저 설치하고 **새 터미널을 연다**:

```powershell
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
```

<details>
<summary>uv를 쓰지 않는 경우 (Python 3.12+ 직접 설치된 환경)</summary>

```powershell
cd C:\projects\S15P11C204\AI
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```
</details>

## A-2. `.env` 만들기

```powershell
cd C:\projects\S15P11C204\AI
Copy-Item .env.example .env
notepad .env
```

| 키 | 누가 쓰나 | 비워두면 |
|---|---|---|
| `GROQ_API_KEY` | 8000 (STT) | **8000 기동 실패** |
| `GMS_KEY` | 8000·8001 (LLM) | 8001은 뜨지만 `/summary`가 503 |
| `SPRING_BASE_URL` | 8000·8002 | Spring 전송만 꺼짐 (감지·수집 자체는 정상) |
| `SPRING_TOKEN` `CHATAI_TOKEN` `COLLECTOR_CONTROL_TOKEN` | 서버 간 인증 | 인증 검사 안 함 (로컬 개발용) |
| `CHATAI_REDIS_URL` | 8001 | 기본값 `redis://127.0.0.1:6379/1` 사용 |

## A-3. 감정 분류 모델 받기 (8001 전용, 641MB)

```powershell
cd C:\projects\S15P11C204\AI
.\.venv\Scripts\Activate.ps1
cd chat-ai
python fetch_model.py
```

이미 받았으면 크기가 맞는 파일은 자동으로 건너뛴다.

## A-4. DB·Redis 띄우기 (8001·Spring이 사용)

```powershell
cd C:\projects\S15P11C204\BackEnd\spring\laive
docker compose up -d
```

`BackEnd\spring\laive\.env`가 있어야 뜬다 — 없으면 백엔드 담당자에게 받을 것.

---

# B. 서버 실행

## B-1. 가상환경 활성화 (터미널마다 1회)

```powershell
cd C:\projects\S15P11C204\AI
.\.venv\Scripts\Activate.ps1
```

프롬프트 앞에 `(AI)` 같은 표시가 붙으면 성공이다.

> **`이 시스템에서 스크립트를 실행할 수 없으므로` 오류가 나면** 실행 정책 때문이다.
> 현재 사용자에게만 허용하면 된다 (관리자 권한 불필요, 1회만):
>
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
> ```

## B-2. 서버 띄우기

**각각 별도 터미널**에서 B-1(활성화)을 한 뒤 아래를 실행한다.
창을 닫거나 `Ctrl+C`를 누르면 서버가 죽는다.

### 8000 — 위험 발언 감지

```powershell
cd C:\projects\S15P11C204\AI
uvicorn app.server:app --port 8000
```

### 8001 — 채팅 감정 분류

```powershell
cd C:\projects\S15P11C204\AI\chat-ai
uvicorn server:app --port 8001
```

### 8002 — 치지직 채팅 수집기

```powershell
cd C:\projects\S15P11C204\AI\chat-collector
uvicorn server:app --port 8002
```

## B-3. (선택) 창 3개를 한 번에

활성화 없이 venv의 실행 파일을 직접 부르는 방식이다.

```powershell
$root = "C:\projects\S15P11C204\AI"
$py = "$root\.venv\Scripts\python.exe"
Start-Process powershell -ArgumentList "-NoExit","-Command","cd '$root'; & '$py' -m uvicorn app.server:app --port 8000"
Start-Process powershell -ArgumentList "-NoExit","-Command","cd '$root\chat-ai'; & '$py' -m uvicorn server:app --port 8001"
Start-Process powershell -ArgumentList "-NoExit","-Command","cd '$root\chat-collector'; & '$py' -m uvicorn server:app --port 8002"
```

---

# C. 확인

```powershell
Invoke-RestMethod http://localhost:8000/health
Invoke-RestMethod http://localhost:8001/health
Invoke-RestMethod http://localhost:8002/health
```

> PowerShell의 `curl`은 `Invoke-WebRequest` 별칭이라 동작이 다르다.
> 굳이 curl을 쓰려면 `curl.exe`로 확장자까지 적을 것.

8001은 브라우저로 <http://localhost:8001/docs> 를 열면 Swagger UI에서 직접 눌러볼 수 있다.
**한글이 들어가는 요청은 Swagger UI로 테스트할 것** — PowerShell 콘솔은 인코딩 문제가 있다.

**8001 응답에 `redis : False`가 보이면** Redis가 안 떠 있는 것이다. 이 상태로는 `/ingest`가 503이다.

```powershell
cd C:\projects\S15P11C204\BackEnd\spring\laive
docker compose up -d redis
```

---

# D. ⚠ Spring 설정 — 빠뜨리면 조용히 안 붙는다

`BackEnd\spring\laive\.env`에 아래 줄이 **없으면** Spring이 AI를 호출하지 않는다.
에러 없이 넘어가고 채팅이 `sentiment_label = NULL`로 저장되며 콘솔의 `services.ai`가
`STOPPED`로 남는다 — **코드가 없는 게 아니라 설정이 없는 것이다.**

```
CHATAI_BASE_URL=http://127.0.0.1:8001
COLLECTOR_BASE_URL=http://127.0.0.1:8002
```

추가 후 **Spring을 재시작**해야 반영된다. 성공하면 기동 로그에서 이 경고가 사라진다:

```
WARN  laive.chat-ai.base-url이 비어 있어 감정 분류 AI를 연결하지 않습니다.
```

---

# E. 실제 방송 없이 채팅 테스트 (리플레이 시뮬레이터)

수집된 과거 VOD 채팅을 **원래 타이밍대로 재생하는 가짜 치지직 서버**다 (포트 8899).
접속 주소만 바꾸면 라이브 방송 없이 개발·백테스트할 수 있다.

```powershell
cd C:\projects\S15P11C204\AI
.\.venv\Scripts\Activate.ps1
cd chat-ai
python replay\replay.py --list
python replay\replay.py --video 14189073 --speed 20
```

```
wss://kr-ss1.chat.naver.com/chat  →  ws://127.0.0.1:8899/chat
```

**채팅 데이터(`data\`)는 git에 없다** — uid·닉네임이 든 개인정보라 제외돼 있다.
데이터 담당자(재민)에게 받아 `AI\chat-ai\data\`에 두거나, 다른 위치에 두고 지정한다:

```powershell
$env:CHATAI_DATA_DIR = "D:\chzzk\data"
python replay\replay.py --list
```

Spring 없이 8001만 시험하려면 `spring_sim.py`가 Spring 역할을 대신한다
(1초 배치로 `/ingest` 호출 → 응답 지표 출력). 터미널 3개가 필요하다:

```powershell
python replay\replay.py --video 14189073 --speed 20   # 터미널 1: 가짜 치지직
uvicorn server:app --port 8001                        # 터미널 2: AI 서버
python replay\spring_sim.py                           # 터미널 3: Spring 역할
```

---

# F. 자주 겪는 문제

| 증상 | 원인·해결 |
|---|---|
| `이 시스템에서 스크립트를 실행할 수 없으므로` | 실행 정책. `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` (B-1 참고) |
| PowerShell에서 `&&` 파서 에러 | Windows PowerShell 5.1은 `&&` 미지원. `;` 로 바꾸거나 줄을 나눌 것 |
| `uvicorn: 명령을 찾을 수 없습니다` | 가상환경 활성화 안 함 (B-1) |
| `Port 8080 was already in use` | Spring이 이미 떠 있다. IDE 실행과 `gradlew bootRun` 중 하나만 쓸 것 |
| `CreateProcess error=206, 파일 이름이 너무 깁니다` | IntelliJ classpath 길이 제한. Run Configuration → Modify options → **Shorten command line** → `@argfile` |
| 8001 `redis : False` | `docker compose up -d redis` |
| 8001 기동 시 모델 없음 에러 | `python fetch_model.py` (A-3) |
| `/summary`가 503 | `AI\.env`에 `GMS_KEY` 없음 |
| 프론트에 감정이 안 보임 | Spring `.env`의 `CHATAI_BASE_URL` 확인 (D) |
| `replay --list`가 비어 있음 | 채팅 데이터 없음 (E) |
| 콘솔에서 한글이 깨짐 | 서버 문제가 아니라 콘솔 인코딩. Swagger UI로 테스트할 것 |

### 포트를 누가 잡고 있는지

```powershell
Get-NetTCPConnection -LocalPort 8001 -State Listen | ForEach-Object { Get-Process -Id $_.OwningProcess }
```

---

# G. 전체 스택 기동 순서

```
1. docker compose up -d      (BackEnd\spring\laive — PostgreSQL + Redis)
2. AI 서버 8000 / 8001 / 8002  (venv 활성화 후, 필요한 것만)
3. Spring 8080               (.env 설정 후)
4. pnpm dev                  (FrontEnd — Electron 창)
```

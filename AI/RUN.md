# AI 서버 실행 가이드

> 대상: **팀 전체**. AI 폴더에는 서버가 **3개** 있고 포트가 각각 다르다.

| 포트 | 서버 | 무슨 일을 하나 | 언제 필요한가 |
|---|---|---|---|
| **8000** | 위험 발언 감지 (`app/`) | 프론트가 보낸 오디오 → STT → 비속어·위험 발언 판정 → Spring에 push | 음성 기능 개발·시연 |
| **8001** | 채팅 감정 분류 (`chat-ai/`) | Spring이 보낸 채팅 → 8감정 분류 + 호응도·여론 지표 + LLM 요약 | 채팅 여론 화면 개발·시연 |
| **8002** | 치지직 채팅 수집기 (`chat-collector/`) | 치지직 접속 → 채팅·시청자 수집 → Spring에 push | 실제 방송 채팅이 필요할 때 |

```
프론트 ──오디오(WS)──▶ 8000 ──▶ Spring
치지직 ──socket.io──▶ 8002 ──▶ Spring ──▶ 8001 ──▶ (감정·지표·요약)
```

**실행 방법은 두 가지다.**

| | 언제 쓰나 |
|---|---|
| **A. Docker** (권장) | 평소 개발·배포. 명령 하나로 3서버가 뜨고, 죽으면 자동으로 되살아난다 |
| **B. 직접 실행** | Docker가 안 될 때, 디버거를 붙일 때 |

아래 예시의 `C:\projects\S15P11C204`는 **본인 저장소 경로로 바꿔서** 쓸 것.

---

# A. Docker로 실행 (권장)

## A-0. 최초 1회 준비

### `.env` 만들기

```powershell
cd C:\projects\S15P11C204\AI
Copy-Item .env.example .env
notepad .env
```

| 키 | 누가 쓰나 | 비워두면 |
|---|---|---|
| `GROQ_API_KEY` | 8000 (STT) | **8000 기동 실패** |
| `GMS_KEY` | 8000·8001 (LLM) | 8001은 뜨지만 `/summary`가 503 |
| `SPRING_BASE_URL` | 8000·8002 | compose가 값을 넣어주므로 비워도 된다 |
| `CHATAI_TOKEN` 등 토큰류 | 서버 간 인증 | 인증 검사 안 함 (로컬 개발용) |

### 백엔드 인프라 먼저 띄우기

AI 서버가 **백엔드의 Redis·PostgreSQL과 같은 네트워크(`laive_default`)** 를 쓰므로 먼저 떠 있어야 한다.

```powershell
cd C:\projects\S15P11C204\BackEnd\spring\laive
docker compose up -d
```

## A-1. 실행

```powershell
cd C:\projects\S15P11C204\AI
docker compose up -d
```

**첫 실행은 5~10분** 걸린다 (이미지 빌드 + 감정 분류 모델 641MB 다운로드).
두 번째부터는 **10초 안에** 뜬다 — 모델이 볼륨에 남기 때문이다.

## A-2. 상태 확인

```powershell
docker compose ps
docker compose logs -f              # 전체 로그 (Ctrl+C로 빠져나옴)
docker compose logs -f chat-ai      # 특정 서버만
```

```powershell
Invoke-RestMethod http://localhost:8000/health
Invoke-RestMethod http://localhost:8001/health
Invoke-RestMethod http://localhost:8002/health
```

8001 응답의 `redis : True`를 확인할 것. `False`면 백엔드 compose가 안 떠 있는 것이다.

## A-3. 코드를 고쳤을 때

**아무것도 안 해도 된다.** 소스가 컨테이너에 연결되어 있고 `--reload`가 켜져 있어
파일을 저장하면 서버가 스스로 재시작한다 (`compose.override.yaml`이 하는 일).

의존성(`pyproject.toml`)을 바꿨을 때만 다시 빌드한다:

```powershell
docker compose up -d --build
```

## A-4. 중지·정리

```powershell
docker compose down                 # 중지 (모델·로그는 남는다)
docker compose down -v              # 볼륨까지 삭제 — 다음 실행 때 모델을 다시 받는다
```

## A-5. 알아둘 것

**개발과 배포는 설정이 다르다.** `compose.override.yaml`이 있으면 compose가
자동으로 합쳐 읽는다. 배포에서는 이 파일을 빼면 된다.

| | `docker compose up -d` (개발) | `docker compose -f compose.yaml up -d` (배포) |
|---|---|---|
| 소스 | 컨테이너에 연결 (수정 즉시 반영) | 이미지에 포함 |
| 재시작 | `no` — 죽으면 그대로 (로그 확인용) | **`unless-stopped` — 자동 복구** |

**모델을 이미지에 굽는 옵션.** 기본은 첫 실행 때 Google Drive에서 받는데,
배포 이미지는 모델을 포함해 구울 수 있다. 그러면 그 이미지를 받는 쪽은
**Drive에 접근하지 않고** 뜬다 (Drive 공유가 막혀도 배포가 살아남는다).

```powershell
docker build --build-arg BAKE_MODEL=true -t laive-ai:prod .
```

---

# B. 직접 실행 (Docker 없이)

## B-1. 최초 1회

```powershell
cd C:\projects\S15P11C204\AI
uv sync
```

`uv`가 없으면 설치하고 **새 터미널**을 연다:

```powershell
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
```

<details>
<summary>uv를 쓰지 않는 경우 (Python 3.12+ 직접 설치)</summary>

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```
</details>

감정 분류 모델 받기 (8001 전용, 641MB):

```powershell
.\.venv\Scripts\Activate.ps1
cd chat-ai
python fetch_model.py
```

## B-2. 가상환경 활성화 (터미널마다 1회)

```powershell
cd C:\projects\S15P11C204\AI
.\.venv\Scripts\Activate.ps1
```

프롬프트 앞에 `(AI)` 표시가 붙으면 성공이다.

> **`이 시스템에서 스크립트를 실행할 수 없으므로` 오류가 나면** 실행 정책 때문이다.
> 관리자 권한 없이 1회만 실행하면 된다:
>
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
> ```

## B-3. 서버 띄우기

**각각 별도 터미널**에서 B-2를 한 뒤 실행한다. 창을 닫거나 `Ctrl+C`를 누르면 죽는다.

```powershell
# 8000 — 위험 발언 감지
cd C:\projects\S15P11C204\AI
uvicorn app.server:app --port 8000
```

```powershell
# 8001 — 채팅 감정 분류
cd C:\projects\S15P11C204\AI\chat-ai
uvicorn server:app --port 8001
```

```powershell
# 8002 — 치지직 채팅 수집기
cd C:\projects\S15P11C204\AI\chat-collector
uvicorn server:app --port 8002
```

---

# C. ⚠ Spring 설정 — 빠뜨리면 조용히 안 붙는다

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

# D. 로그

Docker든 직접 실행이든 **`AI\logs\`에 파일로 남는다** (10MB × 5개 자동 로테이션).

```
AI\logs\chatai.log      8001
AI\logs\voice.log       8000
```

요청 1건이 1줄이고, 1초를 넘기면 `WARNING`으로 올라간다:

```
2026-07-31 14:23:15 INFO  [chatai] POST /broadcasts/42/ingest 200 51ms bid=42 chats=9 pred=38ms redis=12ms
```

`pred`(추론)와 `redis`가 나뉘어 있어 **느릴 때 어디가 느린지** 바로 보인다.

```powershell
Select-String -Path logs\chatai.log -Pattern "WARNING"
Select-String -Path logs\chatai.log -Pattern "bid=42"
```

---

# E. 테스트

## E-1. 단위 테스트

```powershell
cd C:\projects\S15P11C204\AI\chat-ai
..\.venv\Scripts\python.exe -m pytest -q
```

```
63 passed in 1.33s
```

> **Docker 컨테이너 안에서는 안 돈다** (`No module named pytest`).
> 이미지를 `uv sync --no-dev`로 빌드해 개발 의존성을 뺐기 때문이다 — 고장이 아니라
> **의도한 것**이다(이미지 크기 축소). 테스트는 위처럼 호스트 venv에서 돌린다.
> 호스트 venv가 없으면 B-1을 먼저 할 것.

## E-2. API를 직접 찔러보기

⚠ **한글이 든 본문을 `curl -d`로 바로 보내면 실패한다.** 콘솔이 UTF-8이 아니라
cp949로 인코딩해 서버가 JSON을 못 읽는다:

```
{"detail":"There was an error parsing the body"}
```

**해결 1 — Swagger UI (가장 쉽다).** 브라우저가 UTF-8로 보내주므로 인코딩 문제가 없다.

```
http://localhost:8001/docs      채팅 감정 분류
http://localhost:8000/docs      위험 발언 감지
http://localhost:8002/docs      채팅 수집기
```

**해결 2 — curl에 UTF-8 파일로 넘기기.** 본문을 파일로 저장하고 `--data-binary`로 보낸다.
(`-d @파일`이 아니라 **`--data-binary`** 여야 한다 — `-d`는 줄바꿈을 지운다.
`Out-File -Encoding utf8`이 BOM을 붙이지만 서버가 무시하므로 문제없다 — 확인함)

```powershell
'{"viewers":100,"chats":[{"uid":"u1","msg":"ㅋㅋㅋ 개웃기네"}]}' |
    Out-File -Encoding utf8 body.json
curl.exe -X POST http://localhost:8001/broadcasts/999/ingest `
    -H "Content-Type: application/json" --data-binary "@body.json"
```

정상 응답:

```json
{"results":[{"label":"웃음/재미","score":0.9921}],
 "window":{"chatCount":1,"uniqueChatters":1,"engagement":0.01,"dominant":"웃음/재미", ...}}
```

**해결 3 — PowerShell.** `Invoke-RestMethod`는 쓰지 말 것. 요청은 나가지만
**응답 한글이 `ìì/ì¬ë¯¸`처럼 깨진다** — PowerShell 5.1이 응답을
UTF-8로 안 읽는다. `Invoke-WebRequest`로 받아 직접 디코딩해야 한다:

```powershell
$body = @{
    viewers = 100
    chats = @(@{ uid = "u1"; msg = "ㅋㅋㅋ 개웃기네" })
} | ConvertTo-Json -Depth 5

$resp = Invoke-WebRequest -UseBasicParsing `
    -Uri http://localhost:8001/broadcasts/999/ingest -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body ([Text.Encoding]::UTF8.GetBytes($body))

$r = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
$r.results | Format-Table label, score -AutoSize
$r.window.dominant
```

```
label     score
-----     -----
웃음/재미 0.9921

웃음/재미
```

요청(`UTF8.GetBytes`)과 응답(`UTF8.GetString`) **양쪽 다** 손대야 한다.
번거로우면 Swagger UI를 쓰는 게 낫다.

## E-3. 실제 방송 없이 채팅 테스트 (리플레이 시뮬레이터)

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
데이터 담당자(재민)에게 받아 `AI\chat-ai\data\`에 두거나 환경변수로 지정한다:

```powershell
$env:CHATAI_DATA_DIR = "D:\chzzk\data"
```

Spring 없이 8001만 시험하려면 `spring_sim.py`가 Spring 역할을 대신한다.
터미널 3개가 필요하다:

```powershell
python replay\replay.py --video 14189073 --speed 20   # 터미널 1: 가짜 치지직
uvicorn server:app --port 8001                        # 터미널 2: AI 서버
python replay\spring_sim.py                           # 터미널 3: Spring 역할
```

---

# F. 자주 겪는 문제

| 증상 | 원인·해결 |
|---|---|
| `network laive_default not found` | 백엔드 compose를 먼저 띄울 것 (A-0) |
| 8001 첫 기동이 오래 걸림 | 모델 641MB 다운로드 중. `docker compose logs -f chat-ai`로 진행 확인 |
| 8001 `redis : False` | 백엔드 Redis가 안 떠 있음 |
| `/summary`가 503 | `AI\.env`에 `GMS_KEY` 없음 |
| 컨테이너가 계속 재시작 | `docker compose logs <서비스>`로 원인 확인. 대개 `.env` 누락 |
| Docker에서 코드 수정이 반영 안 됨 | `compose.override.yaml`이 있는지 확인. 없으면 배포 모드로 도는 것 |
| `There was an error parsing the body` | 한글 본문 인코딩. `-d` 대신 Swagger UI나 `--data-binary` (E-2) |
| 컨테이너 안에서 `No module named pytest` | 정상. 배포 이미지는 개발 의존성을 뺐다. 호스트 venv에서 돌릴 것 (E-1) |
| `이 시스템에서 스크립트를 실행할 수 없으므로` | 실행 정책 (B-2 참고) |
| PowerShell에서 `&&` 파서 에러 | Windows PowerShell 5.1은 `&&` 미지원. `;`로 바꾸거나 줄을 나눌 것 |
| `uvicorn: 명령을 찾을 수 없습니다` | 가상환경 활성화 안 함 (B-2) |
| `Port 8080 was already in use` | Spring이 이미 떠 있다. IDE 실행과 `gradlew bootRun` 중 하나만 쓸 것 |
| `CreateProcess error=206, 파일 이름이 너무 깁니다` | IntelliJ classpath 길이 제한. Run Configuration → Modify options → **Shorten command line** → `@argfile` |
| 프론트에 감정이 안 보임 | Spring `.env`의 `CHATAI_BASE_URL` 확인 (C) |
| `replay --list`가 비어 있음 | 채팅 데이터 없음 (E-3) |
| 콘솔에서 한글이 깨짐 | 서버 문제가 아니라 콘솔 인코딩. Swagger UI(`/docs`)로 테스트할 것 |

### 포트를 누가 잡고 있는지

```powershell
Get-NetTCPConnection -LocalPort 8001 -State Listen | ForEach-Object { Get-Process -Id $_.OwningProcess }
```

---

# G. 전체 스택 기동 순서

```
1. docker compose up -d    (BackEnd\spring\laive — PostgreSQL + Redis)
2. docker compose up -d    (AI — 서버 3종)
3. Spring 8080             (.env 설정 후)
4. pnpm dev                (FrontEnd — Electron 창)
```

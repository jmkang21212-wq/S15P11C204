# LAIVE 설치·실행 가이드 (AI / BE / FE 통합)

> 기준: 2026-08-03 `dev` 브랜치. 처음 클론한 사람이 전체 스택을 띄우는 것을 목표로 한다.
> AI 서버의 상세 운영·트러블슈팅은 [`AI/RUN.md`](AI/RUN.md)가 정본이며, 여기서는 전체 흐름과
> 다른 파트를 포함해 정리한다. EC2 배포는 8절.

## 0. 무엇이 몇 번 포트에 뜨나

| 포트 | 구성요소 | 로컬 개발 | EC2 배포 |
|---|---|---|---|
| 5432 | PostgreSQL (TimescaleDB) | Docker — `BackEnd/spring/laive/compose.yaml` | 동일 |
| 6379 | Redis | Docker — 〃 | 동일 |
| 5050 / 5540 | pgAdmin / RedisInsight (선택) | Docker — 〃 | 동일 |
| **8080** | **Spring 백엔드** | `./gradlew bootRun` | Docker `spring` 서비스 (127.0.0.1 바인딩) |
| 80/443 | nginx (리버스 프록시) | 없음 | EC2 호스트에 직접 설치 — 8080 프록시 |
| 8000 | AI — 위험 발언 감지 (STT) | Docker 또는 uvicorn | Docker |
| 8001 | AI — 채팅 감정 분류·요약 | Docker 또는 uvicorn | Docker |
| 8002 | AI — 치지직 채팅 수집기 | Docker 또는 uvicorn | Docker |
| 5173 | 프론트 (Electron dev) | `pnpm dev` | (데스크톱 앱 배포) |

```
프론트(Electron) ──HTTP──▶ Spring 8080 ──▶ PostgreSQL / Redis
프론트 ──오디오 WS──▶ AI 8000 ──▶ Spring
치지직 ──▶ AI 8002(수집) ──▶ Spring ──▶ AI 8001(감정) ──▶ Spring
```

## 1. 공통 사전 설치 (최초 1회)

| 도구 | 버전 | 용도 | 확인 명령 |
|---|---|---|---|
| Docker Desktop | 최신 | DB·Redis·AI 서버 | `docker --version` |
| JDK | **21** | Spring | `java --version` |
| uv (또는 Python 3.12) | 최신 | AI 서버 | `uv --version` |
| Node.js | 20 이상 | 프론트 | `node --version` |
| pnpm | 최신 | 프론트 패키지 매니저 (**npm 아님** — `pnpm-lock.yaml` 사용) | `pnpm --version` |

> 셸 주의: 이 문서의 명령은 전부 **PowerShell** 기준이다 (`AI/RUN.md`와 동일).
> Windows PowerShell 5.1은 `&&`를 지원하지 않으므로 명령을 줄 단위로 나눠 놨다 —
> 블록을 통째로 복사해 붙여넣으면 위에서부터 차례로 실행된다.

## 2. `.env` 설정 — 시작 전에 반드시

**실제 값(시크릿)은 git에 없다.** 팀 공유 채널(MatterMost)에서 받아서 채운다.
값을 커밋하지 말 것 — `.env`는 gitignore에 있다.

### 2-1. 백엔드 — `BackEnd/spring/laive/.env` (필수)

`.env.example`이 아직 없으므로 아래 변수 목록을 보고 파일을 만든다.
**Spring Boot는 `.env`를 스스로 읽지 않는다** — 실행 시 셸에서 로드한다(3절).

| 그룹 | 변수 | 설명 |
|---|---|---|
| Docker 인프라 | `POSTGRES_PASSWORD` | DB 비밀번호 (compose와 Spring이 공유) |
| | `PGADMIN_DEFAULT_EMAIL` / `PGADMIN_DEFAULT_PASSWORD` | pgAdmin 로그인 (로컬 관리용) |
| 로그인·토큰 | `JWT_SECRET` | Access Token 서명 키. **32바이트 이상** 아니면 기동 실패 |
| | `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` / `NAVER_REDIRECT_URI` | 네이버 OAuth 로그인 |
| | `OAUTH_ALLOWED_RETURN_URIS` | 로그인 후 돌아갈 수 있는 프론트 주소 화이트리스트 |
| | `AUTH_COOKIE_SECURE` | 로컬 `false`, 배포(HTTPS) `true` |
| | `DEV_LOGIN_ENABLED` | `true`면 개발용 로그인(`POST /api/v1/auth/dev/login`) 활성화 |
| 플랫폼 연동 | `CHZZK_CLIENT_ID` / `CHZZK_CLIENT_SECRET` / `CHZZK_REDIRECT_URI` | 치지직 OAuth |
| | `PLATFORM_TOKEN_KEY` | 플랫폼 토큰 암호화 키 (base64, 32바이트) |
| AI 연동 | `CHATAI_BASE_URL` / `CHATAI_TOKEN` | 채팅 감정 서버(8001). **비우면 에러 없이 감정 분류가 조용히 꺼진다** |
| | `COLLECTOR_BASE_URL` / `COLLECTOR_CONTROL_TOKEN` | 채팅 수집기(8002). 로컬은 `http://127.0.0.1:8002` |
| | `INTERNAL_SERVICE_TOKEN` | AI→Spring 내부 API 인증 토큰 (32바이트 이상). AI 쪽 `SPRING_TOKEN`과 같은 값 |
| 리포트 AI | `REPORT_AI_ENABLED` / `REPORT_AI_MODEL_ENABLED` / `REPORT_AI_API_KEY` / `REPORT_AI_BASE_URL` / `REPORT_AI_MODEL` / `REPORT_AI_TEMPERATURE` | 세션 리포트 요약 LLM (GMS 프록시) |
| 성장 AI | `GROWTH_AI_ENABLED` / `GROWTH_AI_PROMPT_VERSION` / `GROWTH_ROADMAP_AI_ENABLED` / `GROWTH_ROADMAP_AI_PROMPT_VERSION` | 성장 액션·로드맵 생성 |
| 클립 | `CLIP_STORAGE_DIR` / `CLIP_PUBLIC_BASE_URL` | 하이라이트 클립 저장 경로·공개 URL |
| 하이라이트 튜닝 | `HIGHLIGHT_WARMUP`, `HIGHLIGHT_ENGAGE_WINDOW`, `HIGHLIGHT_Z_ENTER`, `HIGHLIGHT_ENTER_TICKS`, `HIGHLIGHT_MIN_WINDOW_CHATS`, `HIGHLIGHT_EXIT_TICKS`, `HIGHLIGHT_LEAD`, `HIGHLIGHT_MAX_ACTIVE`, `HIGHLIGHT_COOLDOWN` | 하이라이트 감지 파라미터 — 기본 공유값 그대로 쓰면 된다 |

### 2-2. AI — `AI/.env` (필수)

`AI/.env.example`을 복사해 만든다. 상세는 `AI/RUN.md` A-0 참조.

| 변수 | 설명 | 비우면 |
|---|---|---|
| `GROQ_API_KEY` | 8000 STT | 8000 기동 실패 |
| `GMS_KEY` | 8000·8001 LLM | 8001은 뜨지만 `/summary` 503 |
| `SPRING_BASE_URL` / `SPRING_TOKEN` | Spring 호출 주소·인증 (`INTERNAL_SERVICE_TOKEN`과 동일 값) | Docker는 compose가 주입 |
| `CHATAI_TOKEN` / `COLLECTOR_CONTROL_TOKEN` | Spring→AI 인증 (BE `.env`와 동일 값) | 로컬은 인증 생략됨 |
| `CHATAI_REDIS_URL` / `VOICE_REDIS_URL` | Redis 주소 (팀 Redis DB 1번 사용) | 기본값 사용 |
| `COLLECTOR_VIEWER_HASH_SALT` | 시청자 uid 해시 솔트 (개인정보 보호) | — |
| `VOICE_AUTH_DEV_BYPASS` | 8000 개발용 인증 우회 | — |

### 2-3. 프론트 — 선택 (기본값으로 동작)

FE는 `.env` 없이도 로컬 기본값으로 돈다. 다른 주소를 쓸 때만 `FrontEnd/.env` 생성:

| 변수 | 기본값 |
|---|---|
| `VITE_API_ORIGIN` | `http://localhost:8080` |
| `VITE_OAUTH_RETURN_URI` | `http://localhost:5173/oauth/callback` |
| `VITE_AI_WS_URL` | `ws://localhost:8000/ws/audio` |

## 3. 백엔드 (Spring) 실행

```powershell
# ① DB·Redis 먼저 — 안 띄우면 Flyway 접속 실패로 부팅이 죽는다
cd BackEnd\spring\laive
docker compose up -d
```

> compose.yaml에 `spring` 서비스도 있지만 `profiles: ["app"]`이 걸려 있어
> **위 명령으로는 뜨지 않는다** — 배포 전용이다(8절). 로컬 개발은 아래처럼
> bootRun으로 돌리는 게 기본이고, 그래서 코드 수정이 이미지 재빌드 없이 바로 반영된다.

```powershell
# ② .env를 환경변수로 로드하고 서버 실행 (같은 터미널에서 이어서)
#    Spring Boot는 .env 파일을 스스로 읽지 않는다 — 이 로드가 없으면 기동 실패
cd BackEnd\spring\laive
Get-Content .env | Where-Object { $_ -match '^\s*[^#].+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path ("env:" + $name.Trim()) -Value $value.Trim()
}
$env:DEV_LOGIN_ENABLED = "true"
$env:SERVER_PORT = "8080"
.\gradlew bootRun
```

- 첫 실행 시 Flyway가 마이그레이션을 자동 적용한다. 별도 DDL 실행 불필요.
  (2026-08-03에 기존 V1~V21이 `V1__baseline_schema.sql` 하나로 통합됐다 — **그 이전에
  만든 로컬 DB는 검증에 실패하므로** `docker compose down -v`로 볼륨을 지우고 다시 띄울 것)
- 확인 (새 터미널): `Invoke-RestMethod http://localhost:8080/actuator/health` → `status : UP`
- API 수동 테스트: **Swagger UI** `http://localhost:8080/swagger-ui.html` 권장
  (터미널에서 한글 본문을 보내면 cp949 인코딩 문제로 500이 난다 — `AI/RUN.md` E-2 참조)

테스트 실행 (DB 떠 있어야 함):

```powershell
cd BackEnd\spring\laive
Get-Content .env | Where-Object { $_ -match '^\s*[^#].+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path ("env:" + $name.Trim()) -Value $value.Trim()
}
.\gradlew test
```

IDE(IntelliJ)로 실행할 때는 Run Configuration에 `.env`를 환경변수로 등록하거나
EnvFile 플러그인을 쓴다 — **gradlew와 IDE 실행을 동시에 하면 8080 충돌**.

## 4. AI 서버 3종 실행

정본 가이드: **[`AI/RUN.md`](AI/RUN.md)**. 요약만 적는다.

```powershell
# Docker (권장) — 백엔드 compose가 먼저 떠 있어야 한다 (같은 네트워크 사용)
cd AI
Copy-Item .env.example .env   # 최초 1회, 값 채우기
docker compose up -d
```

- 첫 실행 5~10분 (감정 모델 641MB 다운로드), 이후 10초.
- 확인: `http://localhost:8000/health` `8001/health` `8002/health` — 8001 응답의 `redis: True` 확인.
- 코드 수정은 자동 반영(`--reload`). 의존성 변경 시만 `docker compose up -d --build`.
- Docker 없이 직접 실행(uv), 리플레이 시뮬레이터, 트러블슈팅 표는 `AI/RUN.md` B·E·F 참조.

## 5. 프론트 (Electron) 실행

```powershell
cd FrontEnd
pnpm install     # 최초 1회 (npm install 아님 — pnpm-lock.yaml 기준)
pnpm dev         # Electron 창이 뜬다 (renderer는 5173)
```

- 로그인: 백엔드가 `DEV_LOGIN_ENABLED=true`로 떠 있으면 개발용 로그인 사용 가능.
- 빌드: `pnpm build:win` (타입체크 포함은 `pnpm build`).
- 일부 화면(포트폴리오·매칭·대시보드·일정)은 아직 `USE_MOCK=true`라 백엔드 없이도
  목업으로 렌더링된다 — 연동 여부는 각 `src/renderer/src/api/*.ts` 상단 참조.

## 6. 전체 기동 순서 (요약)

```
① BackEnd\spring\laive  docker compose up -d     (PostgreSQL·Redis)
② AI                    docker compose up -d     (8000·8001·8002)
③ BackEnd               .\gradlew bootRun        (8080 — .env 로드 후, 3절)
④ FrontEnd              pnpm dev                 (Electron)
```

②는 채팅·음성 기능을 쓸 때만 필요하다. 포트폴리오·캘린더 등 DB 기반 기능은 ①③④로 충분.

## 7. 자주 겪는 문제 (BE·공통)

| 증상 | 원인·해결 |
|---|---|
| 부팅 시 `Connection to 127.0.0.1:5432 refused` | DB 컨테이너 안 뜸 → ① 먼저. Docker Desktop 재시작 후 컨테이너가 꺼져 있을 수 있다 |
| `JWT_SECRET must be at least 32 bytes` 또는 placeholder 에러 | `.env` 로드 누락 — 3절의 로드 스니펫을 실행한 **같은 터미널**에서 `gradlew`를 실행할 것 (환경변수는 터미널마다 따로다) |
| `Detected resolved migration not applied to database: N` | dev pull로 마이그레이션이 늘었는데 로컬 DB가 중간 버전만 적용한 상태. 최신 pull 후 재실행하면 Flyway가 순서대로 적용. 브랜치에서 새 마이그레이션을 만들 때는 **dev에 이미 있는 다음 번호를 피할 것** (중복 버전은 기동 실패) |
| `Port 8080 was already in use` | 이미 떠 있는 Spring 확인: `Get-NetTCPConnection -LocalPort 8080 -State Listen` |
| 한글 API 요청이 500 (`Invalid UTF-8 start byte`) | 콘솔 cp949 문제. Swagger UI 권장, 또는 UTF-8 파일로 저장 후 `curl.exe --data-binary "@file"` (PowerShell에서 `curl`은 별칭이므로 **`curl.exe`**) |
| 감정 분류가 조용히 안 됨 (`sentiment_label = NULL`) | BE `.env`의 `CHATAI_BASE_URL` 누락 — `AI/RUN.md` C 참조 |
| 프론트 `pnpm install` 오류 | npm/yarn으로 설치했었다면 `node_modules` 삭제 후 pnpm으로 재설치 |

AI 쪽 문제는 `AI/RUN.md` F의 표가 더 자세하다.

## 8. 배포 (EC2)

> **정상 경로는 Jenkins 자동 배포다** (`Jenkinsfile` + `deploy/jenkins/`). 모든 푸시에
> Spring·AI 테스트가 자동으로 돌고(MR에 jenkins 상태 표시), **master 승격 시** EC2의
> `laive-deploy` 스크립트가 spring + AI 3종을 **함께 재빌드**하고 헬스체크까지 한다 —
> 8-1-1의 "한쪽만 재배포" 함정은 이 경로에선 발생하지 않는다. dev 머지만으로는 배포되지
> 않는다는 점만 기억할 것. 아래 절차는 **수동 배포(비상·핫픽스)용**이다.

구조: EC2 한 대에 도커 컨테이너(spring + postgres + redis + AI 3종, 전부 같은
`laive_default` 네트워크) + **호스트에 직접 설치된 nginx**가 80/443 → 127.0.0.1:8080을
프록시한다. Spring 이미지는 `BackEnd/spring/laive/Dockerfile`로 빌드한다.

### 8-1. 기동

```powershell
# Spring — profiles 때문에 서비스명을 직접 지정해야 뜬다
cd BackEnd/spring/laive
docker compose up -d --build spring

# AI 3종
cd ../../../AI
docker compose -f compose.yaml up -d     # override 없이 = 배포 모드 (AI/RUN.md A-5)
```

### 8-1-1. ⚠ 코드 반영 방식이 개발과 배포에서 다르다 — 재배포 대상을 빼먹지 말 것

| | 로컬 개발 | EC2 배포 |
|---|---|---|
| Spring 코드 | bootRun 재시작이면 반영 | **이미지 재빌드 필수**: `docker compose up -d --build spring` |
| AI(수집기 포함) 코드 | 소스가 컨테이너에 마운트 + `--reload` → 저장 즉시 반영 | **이미지에 구워져 있음** — 재빌드 없이는 옛 코드가 계속 돈다: `docker compose -f compose.yaml up -d --build` |
| DB 스키마 | Flyway 자동 적용 | 동일 — spring 컨테이너 기동 시 자동 적용, 수동 DDL 불필요 |

한쪽만 재배포해도 **에러는 나지 않는다**는 게 함정이다. 수집기↔Spring 사이의 새 필드는
양방향 호환으로 설계돼 있어(구버전은 모르는 필드 무시 / 신버전은 없는 필드 NULL 처리)
버전이 어긋나면 조용히 **기능만 빠진 채** 돈다. 예: 열성 시청자 TOP5(FR-F-03)는 수집기가
닉네임을 보내야 완성되는데, Spring만 재배포하면 닉네임이 계속 NULL이라 화면에 별칭만 뜬다.
**기능 배포 시에는 spring과 AI를 함께 재빌드하는 것을 기본으로 할 것.**

### 8-2. EC2 전용 `.env` 체크리스트 — 로컬 값 그대로 올리면 조용히 죽는 것들

컨테이너 안에서 `127.0.0.1`은 자기 자신이다. 서로는 **서비스 이름**으로 불러야 한다.

**Spring `.env`** (`BackEnd/spring/laive/.env`):

| 변수 | EC2 값 | 로컬 값을 그대로 두면 |
|---|---|---|
| `CHATAI_BASE_URL` | `http://chat-ai:8001` | 에러 없이 감정 분류만 꺼짐 (`sentiment_label=NULL`) |
| `COLLECTOR_BASE_URL` | `http://collector:8002` | 채팅 수집 시작 안 됨 |
| `AUTH_COOKIE_SECURE` | `true` (HTTPS 전제) | 쿠키 보안 저하 |
| `NAVER_REDIRECT_URI` 등 콜백 URI | 실제 도메인으로 | OAuth 로그인 실패 |
| `POSTGRES_HOST`/`REDIS_HOST` | **신경 쓸 필요 없음** — compose가 서비스명으로 강제 주입 | — |

**AI `.env`** (`AI/.env`):

| 변수 | EC2 값 | 이유 |
|---|---|---|
| `SPRING_BASE_URL` | `http://spring:8080` | 기본값 `host.docker.internal`은 **리눅스 도커에 없는 이름**이라 AI→Spring 전송이 전부 실패한다 |
| `SPRING_TOKEN` | Spring의 `INTERNAL_SERVICE_TOKEN`과 **동일 값** | 로컬은 비우면 인증 생략이지만 공개 서버는 필수. 쌍이 어긋나면 401 |
| `CHATAI_TOKEN` / `COLLECTOR_CONTROL_TOKEN` | Spring 쪽 같은 이름 변수와 **동일 값** | 〃 |

### 8-3. 보안 그룹

- 공개: 80/443 (nginx)만
- 8080은 compose가 127.0.0.1에 바인딩하므로 자동 비공개 (0.0.0.0으로 바꾸지 말 것 —
  도커가 iptables에 직접 개방 규칙을 넣어 UFW를 우회한다)
- 8000~8002는 보안 그룹에서 차단 — 컨테이너끼리는 내부 네트워크로 통신하므로 열 필요 없다

### 8-4. 배포 후 확인

```powershell
docker ps                                                        # 전부 healthy인지
docker exec laive-spring env | grep -E "CHATAI_BASE|COLLECTOR_BASE"   # 서비스명인지
curl.exe -s http://127.0.0.1:8080/actuator/health                # EC2 안에서
docker logs laive-spring 2>&1 | grep WARN | head                 # "base-url이 비어" 경고 없어야 함
```

기능 단위 확인 (버전 어긋남 감지 — 8-1-1 참조):

```powershell
# 방송 중 콘솔 스냅샷에 topViewers가 실리고, nickname이 null이 아니면
# 수집기·Spring 둘 다 신버전이다. 별칭(viewer-xxxx)만 계속 나오면 수집기가 구버전이다.
docker exec laive-postgres-1 psql -U laive -d laive -c `
  "SELECT count(*) FILTER (WHERE nickname IS NOT NULL) AS with_nickname, count(*) AS total FROM viewer_profiles;"
```

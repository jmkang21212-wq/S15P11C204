# 배포 가이드

> 대상: EC2 `i15c204.p.ssafy.io` (Ubuntu 24.04, 4 vCPU / 16GB / 309GB)

## 구조

```
 ┌─────────────── 사용자 PC (Electron 앱) ────────────────┐
 │  마이크 캡처 · FFmpeg 클립 편집 · OBS 제어 · UI       │
 └───────────────────────┬────────────────────────────────┘
                         │  https / wss  (443 하나만)
                         ▼
 ┌──────────────── EC2  i15c204.p.ssafy.io ───────────────┐
 │  nginx :443  ← 호스트에 설치됨(도커 아님). 유일한 관문 │
 │     ├── /api/v1/*  ──▶ 127.0.0.1:8080  spring          │
 │     ├── /ws/audio  ──▶ 127.0.0.1:8000  voice           │
 │     ├── /clips/*   ──▶ 127.0.0.1:8080  spring          │
 │     ├── /download/*──▶ /var/www/laive/download (정적)  │
 │     ├── /assets/*  ──▶ /var/www/laive/web     (정적)  │
 │     ├── /p/<slug>  ──▶ 위 web의 index.html (SPA 폴백) │
 │     ├── /discover  ──▶ 위 web의 index.html (SPA 폴백) │
 │     └── /          ──▶ 127.0.0.1:8090  landing         │
 │                                                         │
 │  ┌─ laive_default 네트워크 ─────────────────────────┐  │
 │  │  spring ◀──▶ postgres · redis                    │  │
 │  │      ▲  └──▶ chat-ai(8001) · collector(8002)     │  │
 │  │      └───── voice(8000)                          │  │
 │  └───────────────────────────────────────────────────┘  │
 │  landing 컨테이너는 위 네트워크 밖이다(정적 파일만)     │
 └─────────────────────────────────────────────────────────┘
```

컨테이너 7개(spring · postgres · redis · voice · chat-ai · collector · landing)가 상시
가동하고, nginx만 호스트에 있다. **컨테이너 포트는 전부 `127.0.0.1`에만 바인딩된다** —
이유는 아래 [방화벽](#방화벽--절대-건드리지-말-것) 참고.

브라우저로 접근하는 공개 표면은 세 개이며 전부 인증이 필요 없다 — 랜딩(`/`),
포트폴리오 공개 뷰(`/p/<slug>`), 스트리머 디렉터리(`/discover`). 배포 절차는 아래 5번.

---

## 1. 최초 1회 세팅

### 1-1. 접속

```bash
ssh -i "C:\kiki\I15C204T.pem" ubuntu@i15c204.p.ssafy.io
```

키 파일 권한이 너무 열려 있으면 OpenSSH가 거부한다. Windows에서는 한 번만:

```bash
icacls "C:\kiki\I15C204T.pem" /inheritance:r /grant:r "$($env:USERNAME):R"
```

`~/.ssh/config`에 등록해 두면 `ssh laive`로 줄어든다.

### 1-2. 도커 (설치 완료됨 — 재설치 시에만)

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl && sudo install -m 0755 -d /etc/apt/keyrings && sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc && sudo chmod a+r /etc/apt/keyrings/docker.asc && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null && sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
```

`usermod`은 **재로그인해야** 적용된다.

### 1-3. tmux (권장)

이미지 빌드가 5~10분 걸린다. ssh가 끊기면 빌드도 죽으므로 긴 작업은 tmux 안에서 한다.

```bash
sudo apt-get install -y tmux
tmux new -s laive        # 나올 때 Ctrl+B, D  /  돌아올 때 tmux attach -t laive
```

### 1-4. 저장소

```bash
git clone https://lab.ssafy.com/s15-webmobile1-sub1/S15P11C204.git ~/laive
```

`git pull` 때마다 인증을 묻는 게 번거로우면 Deploy Key를 등록한다:

```bash
ssh-keygen -t ed25519 -C "laive-server" -f ~/.ssh/id_ed25519 -N ""
cat ~/.ssh/id_ed25519.pub      # 출력을 GitLab > Settings > Deploy Keys 에 등록
git -C ~/laive remote set-url origin git@lab.ssafy.com:s15-webmobile1-sub1/S15P11C204.git
```

### 1-5. 정적 파일 디렉터리

설치 파일(`/download/`)과 공개 웹 빌드(`/p/`·`/discover`·`/assets/`)를 담는다.

```bash
sudo mkdir -p /var/www/laive/download /var/www/laive/web
sudo chown ubuntu:ubuntu /var/www/laive/download /var/www/laive/web
```

새 디렉터리만 만든다. **기존 시스템 디렉터리의 퍼미션은 건드리지 않는다** (SSAFY 서버 규정).

### 1-6. nginx

인증서는 SSAFY가 발급해 둔 와일드카드를 그대로 쓴다 — certbot 불필요.

```
/etc/letsencrypt/live/p.ssafy.io/     *.p.ssafy.io    ~2026-09-21
```

```bash
sudo cp ~/laive/deploy/nginx/laive.conf /etc/nginx/sites-available/laive
sudo ln -sf /etc/nginx/sites-available/laive /etc/nginx/sites-enabled/laive
sudo nginx -t && sudo systemctl reload nginx
```

`nginx -t`가 실패하면 **reload하지 말 것.** 실패한 채로 reload하면 이전 설정이 유지되지만,
restart하면 nginx가 아예 안 뜬다.

---

## 2. `.env` 채우기

`.env`는 git에 없다. 로컬에서 복사한 뒤 **서버용으로 값을 고친다.**

```bash
scp C:\LAIVE\BackEnd\spring\laive\.env laive:~/laive/BackEnd/spring/laive/.env
scp C:\LAIVE\AI\.env                   laive:~/laive/AI/.env
```

```bash
chmod 600 ~/laive/BackEnd/spring/laive/.env ~/laive/AI/.env
```

### 2-1. 반드시 바꿔야 하는 값

**`BackEnd/spring/laive/.env`**

| 키 | 배포값 | 안 바꾸면 |
|---|---|---|
| `AUTH_COOKIE_SECURE` | `true` | Refresh 쿠키가 평문 구간으로도 전송된다 |
| `DEV_LOGIN_ENABLED` | `false` | 인증 없이 로그인되는 엔드포인트가 열린다 |
| `SWAGGER_ENABLED` | `false` | API 구조 전체가 문서로 노출된다(nginx가 막지만 이중 잠금) |
| `NAVER_REDIRECT_URI` | `https://i15c204.p.ssafy.io/api/v1/auth/oauth/naver/callback` | 로그인 실패 |
| `CHZZK_REDIRECT_URI` | `https://i15c204.p.ssafy.io/api/v1/platform-accounts/chzzk/callback` | 플랫폼 연동 실패 |
| `OAUTH_ALLOWED_RETURN_URIS` | `laive://app/oauth/callback` (+ 아래 참고) | 앱의 API 요청이 전부 403 |
| `CLIP_PUBLIC_BASE_URL` | `https://i15c204.p.ssafy.io/clips` | 클립 링크가 전부 localhost로 나간다 |
| `CHATAI_BASE_URL` | `http://chat-ai:8001` | 감정 분류가 조용히 꺼진다 |
| `COLLECTOR_BASE_URL` | `http://collector:8002` | 채팅 수집이 안 된다 |

`OAUTH_ALLOWED_RETURN_URIS`는 이름과 달리 **CORS 허용 오리진의 원천이기도 하다.**
`AuthProperties.allowedOrigins()`가 이 목록에서 scheme+host를 뽑아 CORS 허용 목록을
만든다. 즉 여기 없는 오리진에서 온 요청은 인증이 필요 없는 공개 조회까지 403이 된다.

그래서 앱·웹·개발 환경의 오리진을 **콤마로 모두** 넣는다.

```
OAUTH_ALLOWED_RETURN_URIS=https://i15c204.p.ssafy.io/oauth/callback,http://localhost:5173/oauth/callback,laive://app/oauth/callback
```

| 항목 | 무엇을 위한 것인가 |
|---|---|
| `https://i15c204.p.ssafy.io/oauth/callback` | 공개 웹 뷰(같은 오리진). 사실상 없어도 되지만 대칭을 위해 둔다 |
| `http://localhost:5173/oauth/callback` | `pnpm dev`로 배포 서버에 붙는 개발자용. **시연이 끝나면 지울 것** |
| `laive://app/oauth/callback` | **설치 앱(.exe)의 오리진. 이게 없으면 앱이 아무것도 못 한다** |

`laive://app`은 `src/main/index.ts`가 렌더러를 서빙하는 커스텀 스킴이다. 예전처럼
`file://`로 로드하면 오리진이 `null`이 되는데, `null`은 URI로 표현할 수 없어 이 목록에
등록할 방법이 아예 없다 — 설치 앱의 API가 전부 403이던 원인이다. `null`을 허용하도록
BE를 고치면 사용자가 임의로 만든 로컬 HTML도 같은 권한을 갖게 되므로 해서는 안 된다.

이 문자열은 **세 곳에 같이 박힌다.** 바꿀 때 셋을 함께 고친다.

```
src/main/index.ts            RENDERER_SCHEME · RENDERER_HOST
FrontEnd/.env.production     VITE_OAUTH_RETURN_URI
서버 .env                     OAUTH_ALLOWED_RETURN_URIS
```

`POSTGRES_HOST`·`REDIS_HOST`는 건드릴 필요 없다 — compose가 `postgres`/`redis`로 덮어쓴다.

**`AI/.env`**

| 키 | 배포값 |
|---|---|
| `SPRING_BASE_URL` | `http://spring:8080` |
| `GROQ_API_KEY` | 있어야 8000이 뜬다 |
| `GMS_KEY` | 없으면 `/summary`가 503 |

### 2-2. 시크릿은 새로 만든다

로컬 개발값을 그대로 쓰지 않는다. 각각:

```bash
openssl rand -base64 32
```

`POSTGRES_PASSWORD` · `JWT_SECRET` · `PLATFORM_TOKEN_KEY` · `INTERNAL_SERVICE_TOKEN` ·
`CHATAI_TOKEN` · `COLLECTOR_CONTROL_TOKEN` · `PGADMIN_DEFAULT_PASSWORD`

`PLATFORM_TOKEN_KEY`는 **Base64로 인코딩한 32바이트**여야 한다(위 명령의 출력이 그대로 맞다).

### 2-3. 코드 밖에서 해야 하는 것

네이버·치지직 개발자센터에 위 `*_REDIRECT_URI` 두 개를 **등록**한다.
이걸 빠뜨리면 코드가 아무리 맞아도 로그인이 안 된다. 반영에 시간이 걸릴 수 있으니 먼저 해둔다.

---

## 3. 기동

**순서가 중요하다.** AI compose가 백엔드 compose의 `laive_default` 네트워크에 붙는다
(`external: true`). 순서를 지키지 않으면 `network laive_default not found`로 AI가 안 뜬다.

```bash
# 1) 백엔드 — DB·Redis·Spring
cd ~/laive/BackEnd/spring/laive
docker compose up -d --build postgres redis spring
```

> 서비스명을 직접 지정하는 이유: `spring`에 `profiles: ["app"]`이 걸려 있어 기본
> `up -d`로는 뜨지 않는다(팀원들이 IDE에서 8080을 쓰는 개발 흐름을 지키려고).
> 관리도구(pgadmin·redisinsight)도 이 방식으로 자연히 제외된다.

```bash
# 2) AI — 배포 모드(개발용 override 제외)
cd ~/laive/AI
docker compose -f compose.yaml up -d --build
```

> `-f compose.yaml`이 핵심이다. 그냥 `up -d`로 띄우면 `compose.override.yaml`이
> 합쳐지면서 `restart: "no"`가 적용되어 **죽어도 안 살아난다.**

첫 실행은 감정 분류 모델 641MB 다운로드 때문에 5~10분 걸린다. 이미지를 미리 구워두려면:

```bash
docker build --build-arg BAKE_MODEL=true -t laive-ai:latest ~/laive/AI
```

### 확인

```bash
docker ps --format "{{.Names}}\t{{.Status}}"
curl -s localhost:8080/actuator/health
curl -s localhost:8000/health; curl -s localhost:8001/health; curl -s localhost:8002/health
curl -sI https://i15c204.p.ssafy.io/api/v1/auth/health
```

8001 응답의 `redis: True`를 확인한다. `False`면 백엔드 compose가 안 떠 있는 것이다.

한 번 띄우면 `restart: unless-stopped`라 **서버 재부팅에도 자동으로 다시 뜬다.**

여기까지는 앱이 쓰는 경로만 확인한 것이다. 브라우저로 들어오는 공개 화면(랜딩 ·
포트폴리오 · 디렉터리)은 5번에서 따로 올린다.

---

## 4. Electron 앱 배포

```bash
cd C:\LAIVE\FrontEnd
pnpm install
pnpm build:win
```

`.env.production`이 자동으로 읽혀 실서버 주소가 박힌다. 산출물은 `dist/`에 생긴다.

```bash
scp C:\LAIVE\FrontEnd\dist\laive-client-*-setup.exe laive:/var/www/laive/download/laive-setup.exe
scp C:\LAIVE\FrontEnd\dist\latest.yml               laive:/var/www/laive/download/
```

landing 페이지 다운로드 버튼이 `/download/laive-setup.exe`로 고정되어 있으므로, scp할 때
반드시 이 이름으로 저장한다(버전이 박힌 원본 파일명 그대로 올리면 링크가 404).

다운로드 주소:

```
https://i15c204.p.ssafy.io/download/laive-setup.exe
```

### macOS 빌드

mac에서 `pnpm build:mac`으로 만든 `dist/laive-client-*.dmg`를 exe와 같은 자리에 올린다.

```bash
scp dist/laive-client-*.dmg laive:/var/www/laive/download/laive.dmg
```

landing 페이지 macOS 버튼이 `/download/laive.dmg`로 고정되어 있으므로, exe와 마찬가지로
**반드시 이 이름으로 저장한다**(버전이 박힌 원본 파일명 그대로 올리면 링크가 404).

다운로드 주소:

```
https://i15c204.p.ssafy.io/download/laive.dmg
```

> 파일을 Google Drive에 두고 링크만 거는 방법은 쓰지 않는다. Drive에는 파일에 직접
> 대응하는 공개 URL이 없어 공유 링크가 HTML 페이지를 내려주고, 직링크처럼 보이는
> `uc?export=download`도 100MB가 넘으면 바이러스 검사 안내 페이지(HTML)로 바뀐다 —
> 실제 파일은 그 페이지의 일회성 confirm 토큰이 있어야 나오므로 고정 링크가 불가능하다.
> `<a download>` 속성은 크로스 오리진에서 브라우저가 무시하므로 우회 수단이 되지 않는다.

> `dmg.artifactName`에 아키텍처가 들어 있지 않다(`${name}-${version}.${ext}`). Intel과
> Apple Silicon을 둘 다 빌드하면 파일명이 겹치므로, 한쪽만 배포하거나 이름을 나눠야 한다.
>
> `mac.notarize: false`라 공증되지 않은 빌드다. 처음 실행할 때 "확인되지 않은 개발자"
> 경고가 뜨고, 사용자가 우클릭 → 열기로 넘겨야 한다.

> `electron-updater`가 아직 의존성에 없어 **자동 업데이트는 동작하지 않는다.**
> `latest.yml`은 나중에 붙일 때를 위해 같이 올려둔다. 지금은 새 버전을 배포하면
> 사용자가 다시 받아 설치해야 한다.

---

## 5. 공개 웹 배포 — 랜딩 · 포트폴리오 · 디렉터리

브라우저로 들어오는 세 화면이 443 하나를 나눠 쓴다. **Jenkins 파이프라인 밖이라
전부 수동이다** (`deploy/jenkins/scripts/laive-deploy`는 spring·AI만 다룬다).

| 주소 | 화면 | 서빙 방식 |
|---|---|---|
| `/` | 랜딩 | landing 컨테이너 (127.0.0.1:8090) |
| `/p/<slug>` | 포트폴리오 공개 뷰 (SC-10) | `/var/www/laive/web` 정적 + SPA 폴백 |
| `/discover` | 스트리머 디렉터리 (SC-11) | 같음 |
| `/assets/*` | 위 두 화면의 JS·CSS·폰트 | 같음 |

### 5-1. 랜딩 페이지

저장소의 `landing/`을 서버로 옮겨 컨테이너로 띄운다. Jenkins가 관리하는 `~/laive`
워크트리를 더럽히면 배포가 거부되므로 **홈 아래 별도 디렉터리**를 쓴다.

```bash
scp -r -i <pem> landing ubuntu@i15c204.p.ssafy.io:~/
ssh -i <pem> ubuntu@i15c204.p.ssafy.io "cd ~/landing && docker compose up -d --build"
```

`restart: unless-stopped`라 서버 재부팅에도 자동으로 다시 뜬다. nginx 설정은 한 번만
하면 되고(1-6), 이후 랜딩을 고칠 때는 위 두 줄만 반복한다.

### 5-2. 공개 웹 빌드 (포트폴리오 · 디렉터리)

Electron 렌더러 산출물(`out/renderer`)은 **웹에 올릴 수 없다** — HashRouter라 주소가
`/#/p/abc`가 되고, CSP에 `localhost` 내부 주소가 박혀 있으며, `window.api`(preload)에
의존하는 화면이 섞여 있다. 그래서 `src/web`을 진입점으로 하는 별도 빌드를 쓴다.

로컬에서:

```bash
cd FrontEnd && pnpm build:web        # → out/web
```

서버로:

```bash
ssh -i <pem> ubuntu@i15c204.p.ssafy.io "rm -rf /var/www/laive/web/*"
scp -r -i <pem> FrontEnd/out/web/* ubuntu@i15c204.p.ssafy.io:/var/www/laive/web/
ssh -i <pem> ubuntu@i15c204.p.ssafy.io "chmod 755 /var/www/laive/web/assets"
```

nginx는 최초 1회만 반영하면 되고(1-6), 이후 화면을 고칠 때는 위 세 줄만 반복한다.
**자산 파일명에 해시가 붙어 있어 `index.html`을 `no-cache`로 잡아 뒀다** — 새 빌드가
올라가면 브라우저가 바로 새 해시를 참조한다.

확인:

```bash
curl -sI https://i15c204.p.ssafy.io/assets/<빌드된 css 파일명>   # 200 + 실제 크기
curl -sI https://i15c204.p.ssafy.io/discover                    # 200 + index.html 크기
```

`/discover`와 `/p/<slug>`의 `Content-Length`가 `index.html`과 같아야 한다 — SPA 폴백이
동작했다는 뜻이다. 그다음 브라우저에서 `/p/<slug>` 새로고침(F5)까지 확인한다.

### 5-3. 실제로 밟은 함정 세 개

**① `chmod 755`를 매번 해야 한다.** Windows에는 POSIX 퍼미션이 없어 `scp -r`이 원격
디렉터리를 `0700`으로 만든다. nginx는 `www-data`로 도니 들어갈 수 없다. 안의 파일은
`664`로 읽을 수 있어서 더 헷갈린다 — 증상이 403이라 nginx 설정을 의심하게 된다.

**② `nginx -t`만 하고 `reload`를 빠뜨리면 404가 그대로다.** 그 404는 랜딩 컨테이너가
낸 것(`Content-Length: 153`)이고 호스트 nginx의 404는 162바이트다. 크기로 어느 쪽이
응답했는지 가른다.

**③ Tailwind 스캔 범위.** Tailwind v4는 소스에서 발견한 클래스만 CSS로 만들고, 그
탐지 기준이 Vite의 `root`다. 웹 빌드는 root가 `src/web`인데 화면 컴포넌트는
`src/renderer/src` 아래에 있어, 명시하지 않으면 **유틸리티가 한 개도 생성되지 않는다**
(CSS 10 kB, 화면은 스타일 없는 텍스트). `globals.css`의 `@source` 두 줄이 그것을
막는다 — 지우면 재현된다. 정상 크기는 70 kB대다.

세 경우 모두 **HTTP는 200이라 서버 로그에 아무것도 남지 않는다.**

---

## 6. 코드가 바뀌었을 때

```bash
cd ~/laive && git pull

# Spring만 바뀌었으면
cd BackEnd/spring/laive && docker compose up -d --build spring

# AI만 바뀌었으면
cd ~/laive/AI && docker compose -f compose.yaml up -d --build
```

Spring 재빌드는 2~3분, 그동안 Spring만 내려간다(DB·Redis·AI는 유지).

**새 환경변수가 추가되면 서버 `.env`에도 넣어야 한다.** 이게 조용히 터지는
유일한 유형이다 — 기본값이 있으면 에러 없이 잘못된 값으로 돌아간다.

바뀐 곳에 따라:

| 바뀐 것 | 할 일 |
|---|---|
| Electron 앱 | 4번 (빌드 + `.exe` 업로드) |
| 포트폴리오·디렉터리 화면 | 5-2 (웹 빌드 + 업로드 + `chmod`) |
| 랜딩 | 5-1 |
| `deploy/nginx/laive.conf` | 1-6 (`cp` → `nginx -t` → `reload`) |

**포트폴리오가 새로 공개되는 것은 배포가 아니다.** 공개 뷰는 조회 시점에 API로
데이터를 가져오므로, 다른 스트리머가 포트폴리오를 공개하면 `/p/<slug>`와 `/discover`에
재배포 없이 나타난다.

---

## 7. 방화벽 — 절대 건드리지 말 것

SSAFY 서버는 **접속 불가가 되면 복구가 안 된다(초기화만 가능).**

| | |
|---|---|
| ✅ 해도 되는 것 | `sudo ufw allow <포트>` — 데몬 추가 시 |
| ❌ 절대 금지 | `ufw disable` (반드시 enable 유지) |
| ❌ 절대 금지 | 22번 포트 관련 조작 일체 |
| ❌ 절대 금지 | `/home`·시스템 디렉터리 퍼미션 변경, 공개키 삭제 |

현재 구성은 **UFW를 전혀 건드리지 않는다.** 인증서가 이미 있어 certbot(80번)이
필요 없고, 443은 이미 열려 있다.

### 도커는 UFW를 우회한다 ⚠

도커는 포트를 publish할 때 iptables에 자기 규칙을 UFW보다 **앞에** 꽂는다.
그래서 `ports: ["8000:8000"]`처럼 `0.0.0.0`으로 열면 `ufw status`가 차단으로
보여도 실제로는 전 세계에 열린다. EC2 보안그룹이 `tcp(1024~65535)`를 전부
허용하고 있어 그대로 노출된다.

**모든 compose의 `ports`는 반드시 `127.0.0.1:` 접두사를 유지한다.**

```yaml
ports: ["127.0.0.1:8000:8000"]    # ✅
ports: ["8000:8000"]              # ❌ 방화벽 우회
```

---

## 8. 장애 대응

### 로그

```bash
docker compose logs -f spring          # 백엔드 디렉터리에서
docker compose logs -f --tail 100      # 전체
sudo tail -f /var/log/nginx/laive.error.log
```

AI는 파일로도 남는다: `~/laive/AI/logs/voice.log`, `chatai.log`

### 증상별

| 증상 | 원인·해결 |
|---|---|
| `network laive_default not found` | 백엔드 compose를 먼저 띄울 것 (§3) |
| 502 Bad Gateway | 뒤쪽 컨테이너가 안 떠 있음. `docker ps`로 확인 |
| 앱에서 요청이 전부 실패 | CSP 차단. `index.html`에 실서버 도메인이 있는지 확인 |
| 앱이 localhost로 요청 | `.env.production` 없이 빌드됨. 다시 빌드 |
| 로그인이 마지막에 실패 | `OAUTH_ALLOWED_RETURN_URIS` ↔ `VITE_OAUTH_RETURN_URI` 불일치 |
| 로그인 화면에서 네이버가 거부 | 개발자센터에 콜백 URL 미등록 (§2-3) |
| 클립 업로드 413 | nginx `client_max_body_size`. 서버 로그에는 아무것도 안 남는다 |
| WebSocket이 조용히 끊김 | nginx `Upgrade`/`Connection` 헤더 누락 |
| 감정 분류가 안 됨 | `CHATAI_BASE_URL`이 `http://chat-ai:8001`인지 확인 |
| 컨테이너가 계속 재시작 | 대개 `.env` 누락. `docker compose logs <서비스>` |
| 디스크 부족 | `docker system prune -af` (사용 중이 아닌 이미지 정리) |
| `/discover`·`/p/`가 404 (153바이트) | 랜딩 컨테이너가 낸 404 = nginx `reload` 누락 (§5-3 ②) |
| 공개 화면이 스타일 없는 텍스트 | `/assets/`가 403이거나(§5-3 ①) CSS에 클래스가 없음(§5-3 ③) |
| 공개 화면이 흰 화면 | JS·CSS 404. `/assets/` 퍼미션·경로 확인 |
| `/p/<slug>`가 F5에서만 404 | SPA 폴백 미동작. `@public_web` named location 확인 |
| 설치한 `.exe`에서 API가 전부 403 | 서버 `OAUTH_ALLOWED_RETURN_URIS`에 `laive://app/oauth/callback`이 있는지 (§2-1) |
| 앱 창이 흰 화면 | 커스텀 스킴 핸들러가 파일을 못 찾음. 창 제목이 `laive://app/index.html`인지 확인 |

### 되돌리기

```bash
cd ~/laive && git log --oneline -5
git checkout <이전-커밋>
docker compose up -d --build spring
```

nginx 설정만 되돌리려면:

```bash
sudo rm /etc/nginx/sites-enabled/laive
sudo nginx -t && sudo systemctl reload nginx
```

---

## 9. 배포 전 체크리스트

- [ ] 네이버·치지직 개발자센터에 콜백 URL 등록
- [ ] 서버 `.env` 시크릿 전부 새로 생성 (§2-2)
- [ ] `AUTH_COOKIE_SECURE=true` / `DEV_LOGIN_ENABLED=false` / `SWAGGER_ENABLED=false`
- [ ] `.env` 퍼미션 `600`
- [ ] compose의 `ports`가 전부 `127.0.0.1:` 바인딩인지
- [ ] `nginx -t` 통과 **후 `systemctl reload nginx`까지** (§5-3 ②)
- [ ] `docker ps`에 컨테이너 7개 전부 healthy (landing 포함)
- [ ] 앱에서 로그인 → 플랫폼 연동 → 방송 시작 → 음성·채팅 수신 확인
- [ ] 브라우저에서 `/` · `/discover` · `/p/<slug>` 확인 — `/p/<slug>` 새로고침(F5)까지
- [ ] `/var/www/laive/web/assets`가 `755`인지 (§5-3 ①)

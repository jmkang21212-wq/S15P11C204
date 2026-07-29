# 치지직 채팅 수집기

방송 세션별로 치지직 채팅과 시청자 수를 모아 Spring에 보낸다.
Spring의 `ChatCollectorGateway`가 방송 준비·종료 시점에 이 서버를 호출한다.

```
Spring ──POST /collectors/{sessionId}──▶ 수집기 ──socket.io──▶ 치지직
  ▲                                        │
  └──── /internal/v1/sessions/{id}/... ─────┘
        chat-messages:batch, viewer-metrics:batch,
        collector-status, collection-gaps
```

## 실행

```bash
uvicorn server:app --host 0.0.0.0 --port 8002
```

## 제어 API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| POST | `/collectors/{sessionId}` | 수집 시작. 멱등하다 |
| DELETE | `/collectors/{sessionId}?gracePeriodMs=30000` | 수집 종료와 잔여 Flush |
| GET | `/collectors` | 전체 세션 상태 |
| GET | `/collectors/{sessionId}` | 세션 하나의 상태 |
| GET | `/health` | 204 |

인증은 `Authorization: Bearer {COLLECTOR_CONTROL_TOKEN}`. 토큰이 비어 있으면 검사하지 않는다(로컬 개발).

## 수집 흐름

2026-07-28 스파이크로 확인한 순서다.

1. `GET /internal/v1/sessions/{id}/collector-credentials` — Spring에서 치지직 Access Token과 channelId를 받는다
2. `GET /open/v1/sessions/auth` (Bearer 유저 토큰) → `content.url`
3. 그 URL로 socket.io 연결 (`ssio*.nchat.naver.com`)
4. `SYSTEM` 이벤트로 `sessionKey` 수신
5. `POST /open/v1/sessions/events/subscribe/chat?sessionKey=...`
6. `CHAT` 이벤트 수신

## 반드시 지켜야 하는 세 가지

### 1. 자동 재연결을 쓰지 않는다

`socketio.Client(reconnection=False)`로 두고 재핸드셰이크 루프를 직접 돈다.

세션 인증 URL의 쿼리스트링 인증값은 **재사용되지 않는다.** 같은 URL로 다시 붙으면
서버가 `error` 이벤트에 `"auth fail"`을 실어 보내고 끊는다. 복구하려면 2~5단계를
전부 다시 해야 하고 이전 `sessionKey`는 버린다.

`reconnection=True`에 맡기면 매번 auth fail이 나는데도 라이브러리는 그것을 그냥
"재연결 시도 중"으로 로그에 남긴다. **첫 끊김에서 조용히 죽으면서 겉보기에는
정상 재시도처럼 보여** 장애 발견이 늦어진다.

자격 정보는 핸드셰이크마다 새로 받는다. 들고 있던 Access Token이 만료되면 이후
재핸드셰이크가 전부 401로 실패해 남은 방송 내내 수집이 죽는다.

### 2. `sent_at`은 `messageTime`을 쓴다

`eventSentAt`은 `2026-07-28T11:43:32.220788801` 형태의 **타임존 없는 KST 로컬
문자열**이다. UTC로 파싱하면 9시간 틀어지고, 그 오차는 하이라이트 구간과 채팅
리플레이가 방송의 엉뚱한 지점을 가리키는 형태로만 드러나서 알아채기 어렵다.

`sent_at`은 항상 `messageTime`(epoch ms)에서 만든다.

다만 `messageTime`은 클라이언트 시계다(`HIGHLIGHT_SPEC.md` 가드 8). 서버 수신
시각과 `COLLECTOR_MESSAGE_TIME_MAX_SKEW`(기본 300초)를 넘게 벌어지면 수신 시각으로
대체한다 — 어긋난 시계 하나가 `chat_messages`의 먼 과거·미래 청크에 행을 만들고
리플레이 정렬을 깨뜨린다. 대체 건수는 상태의 `clockSkewCorrected`에 쌓인다.

### 3. 수집 공백 구간을 기록한다

끊긴 동안의 채팅은 재생되지 않아 **영구 유실된다.** 유실 자체는 막을 수 없으므로
"이 구간은 못 봤다"를 남긴다.

기록하지 않으면 리포트가 공백 구간을 "조용한 방송"으로 오집계하고, 더 심각하게는
공백 동안 `uniq`가 0으로 떨어져 트리거 판정의 15분 baseline에 인위적으로 낮은 값이
채워진다. 그러면 **복구 직후의 정상 채팅이 급증으로 오탐된다.**

구간은 끊긴 시점에 `endedAt` 없이 먼저 열고, 복구한 뒤 같은 `startedAt`으로 닫는다.
수집기가 공백 도중에 죽어도 시작점은 남는다. 끝내 닫히지 않은 구간은 세션 종료 시
Spring이 종료 시각으로 닫는다. 저장 위치는 `collection_gaps`(V9)다.

## 시청자 수 폴링

`COLLECTOR_VIEWER_POLL_SEC`(기본 10초)마다 조회해 `viewer-metrics:batch`로 보낸다.

**폴링에 실패한 틱은 아무것도 보내지 않는다.** 마지막 정상값을 새 측정치로 다시 써서
보내면 `viewer_metrics`에 진짜와 구분되지 않는 합성 행이 쌓이고, 리포트의
`viewerTimeline`과 평균·최고 시청자가 조용히 왜곡된다.

`HIGHLIGHT_SPEC.md` 가드 4의 "마지막 정상값 최대 5분, 없으면 판정 생략, 0으로 나누지
말 것"은 **저장 계층이 아니라 판정 계층의 규칙이다.** 수집기는 대신 마지막 정상값과
그 나이를 상태로 노출해 판정이 같은 기준을 쓰게 한다.

```json
{ "lastViewerCount": 2417, "lastViewerAgeSec": 12.4, "viewerStale": false }
```

`viewerStale`이 `true`면 판정을 생략해야 하는 상태다. 기준은
`COLLECTOR_VIEWER_STALE_AFTER_SEC`(기본 300초)이며 가드 4의 5분과 같은 값이다.

## 시청자 가명화

플랫폼 원본 식별자는 Spring으로 보내지 않는다(명세 13.3.1). `senderChannelId`를
`sha256:{hex}`로 해시해 `viewerHash`로만 전달한다.

`COLLECTOR_VIEWER_HASH_SALT`를 **반드시 설정할 것.** 치지직 채널 ID는 32자리
16진수라 소금 없이 SHA-256만 걸면 전수 대조로 원본을 되찾을 수 있다.

> 한번 정하면 절대 바꾸지 말 것. 바꾸는 순간 같은 시청자가 다른 해시로 보여
> `viewer_profiles`에 중복 인격이 생기고 과거 데이터와 이어지지 않는다.

## 아직 저장되지 않는 값

`CHAT` 페이로드에서 읽지만 명세 13.3.1에 필드가 없어 Spring으로 보내지 못하는 값이
둘 있다. 상태의 `streamerMessages`, `emojiTotal`로 세고만 있다.

| 치지직 필드 | 쓰임 | 막힌 곳 |
| --- | --- | --- |
| `profile.userRoleCode` | 스트리머 본인 채팅을 여론 집계에서 제외 | 13.3.1 요청과 `chat_messages` 모두 자리가 없다 |
| `emojis` | 이모티콘 밀도 | 위와 같다 |

쓰려면 13.3.1 요청 스키마와 `chat_messages` 컬럼을 함께 늘려야 한다.

## 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `COLLECTOR_CONTROL_TOKEN` | (없음) | 제어 API Bearer 토큰. Spring의 `COLLECTOR_CONTROL_TOKEN`과 같아야 한다 |
| `SPRING_BASE_URL` | `http://127.0.0.1:8080` | Spring 주소 |
| `SPRING_TOKEN` | (없음) | Spring의 `INTERNAL_SERVICE_TOKEN`과 같아야 한다 |
| `COLLECTOR_VIEWER_HASH_SALT` | (없음) | 시청자 해시 소금. 운영 전에 정하고 이후 불변 |
| `COLLECTOR_CHAT_BATCH_SEC` | `1.0` | 채팅 배치 주기 |
| `COLLECTOR_CHAT_BATCH_SIZE` | `100` | 채팅 배치 최대 건수 |
| `COLLECTOR_VIEWER_POLL_SEC` | `10` | 시청자 수 폴링 주기 |
| `COLLECTOR_VIEWER_STALE_AFTER_SEC` | `300` | 가드 4의 마지막 정상값 유효 기간 |
| `COLLECTOR_HEARTBEAT_SEC` | `10` | 상태 보고 주기. Spring의 `heartbeat-timeout`보다 충분히 짧아야 한다 |
| `COLLECTOR_MESSAGE_TIME_MAX_SKEW` | `300` | 이보다 어긋난 `messageTime`은 수신 시각으로 대체 |
| `COLLECTOR_RECONNECT_BACKOFF_START` | `1` | 재핸드셰이크 최초 대기(초) |
| `COLLECTOR_RECONNECT_BACKOFF_MAX` | `30` | 재핸드셰이크 최대 대기(초) |
| `CHZZK_LIVE_STATUS_URL` | 서비스 API | 시청자 수 조회 주소. 공식 계약이 아니라 설정으로 뺐다 |

## 테스트

```bash
python -m pytest tests -q
```

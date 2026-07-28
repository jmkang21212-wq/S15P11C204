# API 명세서

# 1인 스트리머 AI 매니저 API 명세서

| 항목 | 내용 |
| --- | --- |
| 문서 버전 | v1.5 |
| 수정일 | 2026-07-22 |
| 기준 문서 | 프로젝트 명세서, LAIVE ERD 수정본, STREAMPILOT UI 목업 v0.2 |
| API 버전 | v1 |
| 문서 범위 | 핵심 화면 및 Must 기능 + 일정·약속·성장 목표·채팅 리플레이·내부 AI 분석 API 확장 |

---

## 1. 범위와 설계 원칙

### 1.1 포함 범위

- 이메일·소셜 로그인, 비밀번호 관리 및 스트리머 계정
- 방송 플랫폼 OAuth 연동
- 대시보드 데이터
- 방송 세션 시작, 종료 및 실시간 콘솔
- 위험 발언, 채팅 여론, 반복 질문, 약속 브리핑
- 일정 캘린더 관리와 방송 공지 초안·게시
- 약속 전체 목록, 감지 약속 확정·무시 및 이행 상태 관리
- 세션 리포트와 주간·월간 성장 지표
- 성장 목표 조회·수정과 AI 다음 액션 조회
- 하이라이트 조회와 해당 구간 채팅 리플레이
- STT, 위험 발언, 채팅 요약·감정, 반복 질문, 약속, 리포트, 하이라이트, 성장 액션 내부 AI 분석
- 내 포트폴리오 관리
- 다른 스트리머의 공개 포트폴리오 검색 및 조회

### 1.2 이번 개정에서 제외한 기능

- 대시보드 상단의 녹색 리포트 완료 알림
- 알림 목록, 읽음 처리, 알림 배지 API
- 방송 콘솔의 모더레이션 큐
- 채팅 차단, 승인, 무시 등 모더레이션 조치 API
- `MODERATION` WebSocket 이벤트
- 광고 매칭 요청, 추천 점수 산출, 매칭 수락·거절
- 캠페인 등록 및 협업 진행 상태 관리
- 정밀 클립 편집 및 쇼츠 변환

> SC-11의 화면명은 기존 목업과 동일하게 “비즈니스 매칭”을 사용할 수 있지만, API 기능은 **공개 포트폴리오 탐색**으로 한정한다.
> 

### 1.3 ERD 반영 원칙

- 모든 외부 리소스 ID는 ERD에 맞춰 `bigint`를 사용한다.
- API의 `streamerId`는 `STREAMERS.id`를 의미한다.
- 대시보드와 성장 리포트는 별도 테이블을 만들지 않고 관련 테이블을 집계해 반환한다.
- API 응답의 계산 필드는 DB 컬럼과 일대일 대응하지 않아도 된다.
- 공개 포트폴리오는 반드시 `PORTFOLIOS.is_public = true`인 데이터만 노출한다.
- 소셜 전용 계정은 `STREAMERS.password_hash = null`을 허용한다.
- 소셜 계정 식별은 `SOCIAL_ACCOUNTS(provider, provider_user_id)`의 Unique 제약으로 보장한다.
- `LIVE_BRIEFINGS`는 위험 발언·채팅 요약·반복 질문·약속 등 서로 다른 분석 결과를 실시간 콘솔에 전달한 브리핑 피드이며, `created_at`은 브리핑 생성 시각을 저장한다.
- WebSocket 재연결 복구용 이벤트는 ERD 테이블이 아니라 Redis Streams에 단기 보관한다.

### 1.4 Unique 제약

ERD의 관계와 API의 단일 리소스 식별 규칙을 실제 DDL에서도 보장하기 위해 다음 Unique 제약을 적용한다.

| 테이블 | Unique 컬럼 | 목적 |
| --- | --- | --- |
| `STREAMERS` | `email` | 이메일 로그인·비밀번호 재설정 대상의 단일 식별. `null`은 허용한다. |
| `SOCIAL_ACCOUNTS` | `(provider, provider_user_id)` | 동일 소셜 계정의 중복 연결 방지 |
| `PLATFORM_ACCOUNTS` | `(platform, channel_id)` | 동일 플랫폼 채널의 중복 연결 방지 |
| `VIEWER_PROFILES` | `(platform_account_id, viewer_hash)` | 동일 플랫폼 시청자 프로필 중복 생성 방지 |
| `VIEWER_METRICS` | `(session_id, measured_at)` | 지표 배치 재시도 시 동일 시점 중복 방지 |
| `TRANSCRIPTS` | `(session_id, start_ms, end_ms)` | STT Callback 재시도 시 동일 청크 중복 방지 |
| `REPORTS` | `session_id` | 세션당 리포트 1개 보장 |
| `PORTFOLIOS` | `streamer_id` | 스트리머당 포트폴리오 1개 보장 |
| `PORTFOLIOS` | `public_slug` | 공개 포트폴리오 URL의 단일 식별 |
| `STREAMER_TAGS` | `(streamer_id, tag_id)` | 태그 중복 연결 방지 |
| `PORTFOLIO_HIGHLIGHTS` | `(portfolio_id, highlight_id)` | 하이라이트 중복 지정 방지 |
| `PORTFOLIO_HIGHLIGHTS` | `(portfolio_id, display_order)` | 포트폴리오 내 노출 순서 중복 방지 |

### 1.5 Nullable 컬럼

| 테이블.컬럼 | Nullable 사유 |
| --- | --- |
| `STREAMERS.email` | 소셜 제공자가 이메일을 제공하지 않는 소셜 전용 계정 |
| `STREAMERS.password_hash` | 비밀번호를 아직 설정하지 않은 소셜 전용 계정 |
| `SOCIAL_ACCOUNTS.provider_email` | 소셜 제공자가 이메일을 제공하지 않을 수 있음 |
| `AUTH_REFRESH_TOKENS.revoked_at` | 아직 폐기되지 않은 Refresh Token |
| `PLATFORM_ACCOUNTS.refresh_token` | 플랫폼이 Refresh Token을 제공하지 않을 수 있음 |
| `CALENDAR_EVENTS.platform_account_id` | 플랫폼과 무관한 휴방 일정 |
| `CALENDAR_EVENTS.memo` | 메모가 없는 일정 |
| `SCHEDULE_ANNOUNCEMENTS.published_at` | 초안 또는 게시 실패 상태 |
| `BROADCAST_SESSIONS.calendar_event_id` | 캘린더 일정 없이 시작한 방송 |
| `BROADCAST_SESSIONS.thumbnail_url` | 방송 시작 시 썸네일을 확보하지 못한 경우 |
| `BROADCAST_SESSIONS.vod_url` | 방송 중이거나 VOD가 아직 생성되지 않은 경우 |
| `BROADCAST_SESSIONS.ended_at` | 진행 중인 방송 |
| `REPORTS.summary` | `PROCESSING` 또는 AI 요약 실패 상태 |
| `REPORTS.avg_viewers`, `peak_viewers`, `total_chat` | 집계가 아직 완료되지 않은 `PROCESSING` 상태 |
| `REPORTS.positive_ratio`, `neutral_ratio`, `negative_ratio` | 감정 분석이 완료되지 않았거나 일부 실패한 상태 |
| `REPORTS.generated_at` | 리포트 생성이 완료되지 않은 상태 |
| `PROMISES.due_date` | 목업의 “기한 미정” 약속 |
| `HIGHLIGHTS.clip_url`, `thumbnail_url` | 하이라이트 구간 탐지 후 클립 생성 전 또는 생성 실패 상태 |
| `PORTFOLIOS.published_at` | 아직 공개하지 않은 포트폴리오 |

### 1.6 구현 제약과 ERD 외 상태

- 한 스트리머가 동시에 하나의 `LIVE` 세션만 갖도록 방송 시작 시 DB 트랜잭션 잠금과 Redis 세션 잠금을 함께 사용한다.
- 스트리머는 여러 `PLATFORM_ACCOUNTS`를 가질 수 있으므로 포트폴리오 API는 플랫폼 정보를 항상 `platformAccounts` 배열로 반환한다.
- 실시간 현재값은 DB·Redis에서 스냅샷으로 복구하고, 놓치면 안 되는 브리핑·세션 상태 이벤트만 Redis Streams에서 재생한다.
- Redis 이벤트 보관 범위를 초과한 경우에는 이벤트 전체 재생 대신 `GET /broadcast-sessions/{sessionId}/live-snapshot`을 다시 호출한다.
- 내부 AI 변경 요청의 `X-Request-Id` 처리 결과는 Redis에 단기 보관해 재시도에 의한 중복 저장을 방지한다.

---

## 2. 화면별 API

| 화면 | API |
| --- | --- |
| SC-01 로그인 / 플랫폼 연동 | 이메일·소셜 Auth API, Password API, Platform Account API |
| SC-02 대시보드 | `GET /dashboard`, `GET /promises` |
| SC-03 방송 콘솔 | Broadcast Session API, Live Snapshot API, WebSocket, 약속 확정·무시 API |
| SC-04 일정 캘린더 | Calendar Event API, Schedule Announcement API |
| SC-06 세션 리포트 | `GET /broadcast-sessions/{sessionId}/report` |
| SC-07 하이라이트 / 클립 | 하이라이트 목록, 채팅 리플레이 API |
| SC-09 성장 리포트 | `GET /growth-reports`, Growth Goal API |
| SC-10 포트폴리오 | `GET /portfolio/me`, `PATCH /portfolio/me`, 공개 상세 API |
| SC-11 비즈니스 매칭 | `GET /portfolios`, `GET /portfolios/{publicSlug}` |

---

## 3. ERD 매핑

| 기능 | 주요 테이블 |
| --- | --- |
| 인증 | `STREAMERS`, `SOCIAL_ACCOUNTS`, `AUTH_REFRESH_TOKENS`, Redis 비밀번호 재설정 토큰 |
| 태그 | `TAGS`, `STREAMER_TAGS` |
| 플랫폼 연동 | `PLATFORM_ACCOUNTS` |
| 대시보드 | `CALENDAR_EVENTS`, `BROADCAST_SESSIONS`, `REPORTS`, `PROMISES`, `HIGHLIGHTS` |
| 일정·공지 | `CALENDAR_EVENTS`, `SCHEDULE_ANNOUNCEMENTS`, `PLATFORM_ACCOUNTS` |
| 실시간 콘솔 | `BROADCAST_SESSIONS`, `VIEWER_METRICS`, `LIVE_BRIEFINGS`, `RISK_EVENTS`, `SENTIMENT_SNAPSHOTS`, `REPEATED_QUESTIONS`, Redis Streams 이벤트 버퍼 |
| 약속 | `PROMISES`, `TRANSCRIPTS`, `BROADCAST_SESSIONS` |
| 세션 분석 | `TRANSCRIPTS`, `REPORTS`, `REPORT_SEGMENTS`, `REPORT_INSIGHTS`, `HIGHLIGHTS` |
| 성장 지표 | `BROADCAST_SESSIONS`와 `REPORTS` 집계 |
| 성장 목표 | `GROWTH_GOALS`, `GROWTH_ACTIONS`, `REPORTS`, `BROADCAST_SESSIONS` 집계 |
| 하이라이트 채팅 리플레이 | `HIGHLIGHTS`, `REPORTS`, `BROADCAST_SESSIONS`, `CHAT_MESSAGES`, `VIEWER_PROFILES` |
| 포트폴리오 | `PORTFOLIOS`, `STREAMERS`, `STREAMER_TAGS`, `TAGS`, `PORTFOLIO_HIGHLIGHTS` |

---

## 4. 공통 규약

### 4.1 URL과 인증

- Base URL: `/api/v1`
- WebSocket: `/ws/v1`
- 인증 헤더: `Authorization: Bearer {accessToken}`
- Access Token 만료: 30분
- Refresh Token 만료: 14일

### 4.2 데이터 형식

- ID: JSON number, DB `bigint`
- 날짜·시각: ISO 8601 UTC
- 방송 구간: 방송 시작 기준 밀리초인 `startMs`, `endMs`
- 필드명: `camelCase`
- 목록 페이지: `page`는 0부터 시작
- 하이라이트 채팅 리플레이는 시간 순서 보존을 위해 서버가 발급한 불투명 `cursor`를 사용한다.

### 4.3 성공 응답

```json
{
  "success": true,
  "data": {}
}
```

목록 응답:

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

### 4.4 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "방송 세션을 찾을 수 없습니다."
  }
}
```

| HTTP | 사용 기준 |
| --- | --- |
| `200` | 조회·수정 성공 |
| `201` | 생성 성공 |
| `202` | 비동기 작업 접수 |
| `204` | 본문 없는 성공 |
| `400` | 요청값 오류 |
| `401` | 인증 실패 |
| `403` | 접근 권한 없음 |
| `404` | 리소스 없음 |
| `409` | 현재 상태와 요청 충돌 |
| `500` | 서버 오류 |
| `502` | 외부 플랫폼 또는 AI 연동 실패 |

---

## 5. API 목록

이 표는 외부 클라이언트용 API 목록이다. 서비스 간 수집·AI API는 13절에서 별도로 정의한다.

| 구분 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| Auth | POST | `/auth/signup` | 이메일 회원가입 |
| Auth | POST | `/auth/login` | 이메일 로그인 |
| Auth | GET | `/auth/oauth/{provider}/authorize` | 소셜 로그인 시작 |
| Auth | GET | `/auth/oauth/{provider}/callback` | 소셜 로그인 Callback |
| Auth | POST | `/auth/refresh` | Access Token 갱신 |
| Auth | POST | `/auth/logout` | 로그아웃 |
| Password | POST | `/auth/password` | 소셜 전용 계정의 최초 비밀번호 설정 |
| Password | PATCH | `/auth/password` | 기존 비밀번호 변경 |
| Password | POST | `/auth/password-reset-requests` | 비밀번호 재설정 메일 요청 |
| Password | POST | `/auth/password-resets` | 재설정 토큰으로 비밀번호 설정 |
| Streamer | GET | `/streamers/me` | 내 프로필 조회 |
| Streamer | PATCH | `/streamers/me` | 내 프로필 수정 |
| Platform | GET | `/platform-accounts` | 플랫폼 연동 목록 |
| Platform | GET | `/platform-accounts/{platform}/authorize` | 플랫폼 OAuth 시작 |
| Platform | GET | `/platform-accounts/{platform}/callback` | 플랫폼 OAuth Callback |
| Platform | DELETE | `/platform-accounts/{platformAccountId}` | 플랫폼 연동 해제 |
| Calendar | GET | `/calendar-events` | 기간별 일정 목록 |
| Calendar | POST | `/calendar-events` | 일정 생성 |
| Calendar | GET | `/calendar-events/{calendarEventId}` | 일정 상세 |
| Calendar | PATCH | `/calendar-events/{calendarEventId}` | 일정 수정·취소 |
| Announcement | POST | `/calendar-events/{calendarEventId}/announcement-drafts` | AI 공지 초안 생성(보류) |
| Announcement | PATCH | `/schedule-announcements/{announcementId}` | 공지 초안 수정(보류) |
| Announcement | POST | `/schedule-announcements/{announcementId}/publish` | 공지 게시(보류) |
| Dashboard | GET | `/dashboard` | 대시보드 조회 |
| Session | GET | `/broadcast-sessions` | 방송 세션 목록 |
| Session | POST | `/broadcast-sessions` | 방송 분석 세션 시작 |
| Session | GET | `/broadcast-sessions/current` | 진행 중 방송 조회 |
| Session | GET | `/broadcast-sessions/{sessionId}` | 방송 세션 상세 |
| Session | POST | `/broadcast-sessions/{sessionId}/end` | 방송 분석 세션 종료 |
| Live | GET | `/broadcast-sessions/{sessionId}/live-snapshot` | 콘솔 상태 복구 |
| Live | POST | `/broadcast-sessions/{sessionId}/ws-ticket` | WebSocket 티켓 발급 |
| Promise | GET | `/promises` | 약속 전체 목록 |
| Promise | POST | `/promises/{promiseId}/confirm` | 감지 약속 확정 |
| Promise | POST | `/promises/{promiseId}/dismiss` | 감지 약속 무시 |
| Promise | PATCH | `/promises/{promiseId}` | 확정 약속 상태 변경 |
| Report | GET | `/broadcast-sessions/{sessionId}/report` | 세션 리포트 조회 |
| Report | GET | `/growth-reports` | 성장 지표 조회 |
| Growth Goal | GET | `/growth-goals` | 성장 목표와 다음 액션 조회 |
| Growth Goal | PATCH | `/growth-goals/{growthGoalId}` | 성장 목표 수정 |
| Highlight | GET | `/broadcast-sessions/{sessionId}/highlights` | 하이라이트 조회 |
| Highlight | GET | `/highlights/{highlightId}/chat-replay` | 하이라이트 구간 채팅 리플레이(보류) |
| Portfolio | GET | `/portfolio/me` | 내 포트폴리오 조회 |
| Portfolio | PATCH | `/portfolio/me` | 내 포트폴리오 수정 |
| Portfolio | GET | `/portfolios` | 다른 스트리머 포트폴리오 검색 |
| Portfolio | GET | `/portfolios/{publicSlug}` | 공개 포트폴리오 상세 |

---

## 6. 인증 및 계정

### 6.1 이메일 회원가입

`POST /auth/signup`

```json
{
  "email": "streamer@example.com",
  "password": "StrongPass!23",
  "nickname": "한별TV"
}
```

```json
{
  "success": true,
  "data": {
    "streamerId": 101,
    "email": "streamer@example.com",
    "nickname": "한별TV"
  }
}
```

### 6.2 이메일 로그인

`POST /auth/login`

```json
{
  "email": "streamer@example.com",
  "password": "StrongPass!23"
}
```

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "expiresIn": 1800,
    "streamer": {
      "streamerId": 101,
      "nickname": "한별TV",
      "profileImageUrl": "https://cdn.example.com/profile/101.jpg"
    }
  }
}
```

Refresh Token은 `HttpOnly` 쿠키로 전달한다.

### 6.3 소셜 로그인

목업의 네이버·Google 로그인에 사용한다.

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/auth/oauth/{provider}/authorize?returnUri={returnUri}` | 인증 URL로 Redirect |
| GET | `/auth/oauth/{provider}/callback` | 사용자 생성 또는 로그인 후 앱으로 Redirect |

`provider`는 `NAVER` 또는 `GOOGLE`이다. 토큰은 Redirect URL에 포함하지 않는다.

최초 소셜 로그인 시 별도의 이메일 회원가입을 요구하지 않는다.

1. `SOCIAL_ACCOUNTS(provider, provider_user_id)`로 연결 계정을 조회한다.
2. 기존 연결이 없으면 `STREAMERS`를 생성하고 `password_hash`는 `null`로 저장한다.
3. 생성된 스트리머와 `SOCIAL_ACCOUNTS`를 연결한다.
4. 기존 연결이 있으면 연결된 스트리머로 로그인한다.

### 6.4 비밀번호 관리

#### 6.4.1 소셜 전용 계정의 최초 비밀번호 설정

`POST /auth/password`

**인증**: 로그인 필요

`password_hash`가 없는 소셜 전용 사용자가 이메일 로그인을 추가할 때 사용한다. 등록 이메일이 있어야 하며, 이메일 소유가 확인된 계정만 허용한다.

```json
{
  "newPassword": "NewPass!45",
  "newPasswordConfirm": "NewPass!45"
}
```

성공 시 `204 No Content`를 반환한다. 이미 비밀번호가 존재하면 `409 Conflict`를 반환한다.

#### 6.4.2 로그인 상태에서 비밀번호 변경

`PATCH /auth/password`

**인증**: 로그인 필요

```json
{
  "currentPassword": "OldPass!23",
  "newPassword": "NewPass!45",
  "newPasswordConfirm": "NewPass!45"
}
```

- 현재 비밀번호가 일치해야 한다.
- 새 비밀번호는 8~64자이며 영문, 숫자, 특수문자를 포함한다.
- 기존 비밀번호와 동일한 값은 허용하지 않는다.
- 성공 시 모든 Refresh Token을 폐기하고 `204 No Content`를 반환한다.
- 사용자는 새 비밀번호로 다시 로그인한다.

#### 6.4.3 비밀번호 재설정 메일 요청

`POST /auth/password-reset-requests`

**인증**: 불필요

```json
{
  "email": "streamer@example.com"
}
```

계정 존재 여부가 노출되지 않도록 항상 다음과 같은 `202 Accepted` 응답을 반환한다.

```json
{
  "success": true,
  "data": {
    "message": "해당 이메일로 등록된 계정이 있으면 재설정 안내를 발송합니다."
  }
}
```

재설정 토큰은 원문을 저장하지 않고 해시하여 Redis에 최대 30분간 보관한다. 동일 이메일의 기존 토큰은 새 요청 시 폐기한다.

#### 6.4.4 재설정 토큰으로 비밀번호 설정

`POST /auth/password-resets`

**인증**: 불필요

```json
{
  "token": "pw_reset_01J2ABC...",
  "newPassword": "NewPass!45",
  "newPasswordConfirm": "NewPass!45"
}
```

- 토큰은 1회만 사용할 수 있다.
- 소셜 전용 계정도 등록 이메일 인증이 완료되면 이 API로 이메일 로그인을 추가할 수 있다.
- 성공 시 기존 Refresh Token을 모두 폐기하고 `204 No Content`를 반환한다.

### 6.5 토큰 갱신과 로그아웃

| Method | Endpoint | 응답 |
| --- | --- | --- |
| POST | `/auth/refresh` | 새 Access Token |
| POST | `/auth/logout` | `204 No Content` |

로그아웃 시 `AUTH_REFRESH_TOKENS.revoked_at`을 기록한다.

### 6.6 내 스트리머 프로필

`GET /streamers/me`

```json
{
  "success": true,
  "data": {
    "streamerId": 101,
    "email": "streamer@example.com",
    "hasPassword": true,
    "loginMethods": ["EMAIL", "GOOGLE"],
    "nickname": "한별TV",
    "profileImageUrl": "https://cdn.example.com/profile/101.jpg",
    "bio": "FPS와 롤토체스를 방송합니다.",
    "tags": [
      { "tagId": 1, "name": "FPS", "tagType": "CONTENT" },
      { "tagId": 2, "name": "롤토체스", "tagType": "CONTENT" }
    ]
  }
}
```

`PATCH /streamers/me`

수정 가능 필드는 `nickname`, `profileImageUrl`, `bio`, `tagIds`이다.

---

## 7. 방송 플랫폼 연동

### 7.1 연동 목록 조회

`GET /platform-accounts`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "platformAccountId": 15,
        "platform": "CHZZK",
        "channelId": "ch_12345",
        "channelName": "한별TV",
        "connectionStatus": "CONNECTED",
        "connectedAt": "2026-07-20T09:00:00Z"
      }
    ]
  }
}
```

### 7.2 OAuth 연동

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/platform-accounts/{platform}/authorize?returnUri={returnUri}` | 플랫폼 인증 URL 반환 |
| GET | `/platform-accounts/{platform}/callback` | 토큰 저장 후 앱으로 Redirect |
| DELETE | `/platform-accounts/{platformAccountId}` | 연동 해제 (`204`) |

`platform`은 `CHZZK` 또는 `SOOP`이다.

authorize는 Redirect가 아니라 인증 URL을 본문으로 반환한다. 플랫폼 연동은 로그인한 회원에 계정을 붙이는 동작이라 Access Token이 필요한데, 브라우저 Redirect에는 인증 헤더를 실을 수 없기 때문이다. 클라이언트는 받은 URL을 외부 브라우저로 연다.

```json
{
  "success": true,
  "data": {
    "authorizeUrl": "https://chzzk.naver.com/account-interlock?clientId=...&redirectUri=...&state=..."
  }
}
```

callback은 인증 없이 열려 있으며, 연동 주체는 요청 파라미터가 아니라 authorize 단계에서 발급한 일회용 `state`에 묶인 회원으로만 결정한다. 처리 후에는 `returnUri`로 Redirect한다.

- 성공: `{returnUri}?platform=CHZZK&status=connected`
- 실패: `{returnUri}?error={오류 코드}` (예: `PLATFORM_ALREADY_LINKED`)
- `state`가 만료·위조되어 복귀 주소를 알 수 없으면 공통 오류 Envelope로 `400`을 반환한다.

Access Token과 Refresh Token은 `PLATFORM_ACCOUNTS`에 암호화하여 저장하며 외부 API 응답에 포함하지 않는다. 동일 채널을 다른 회원이 이미 연동했다면 `409 PLATFORM_ALREADY_LINKED`이며, 같은 회원의 재연동은 기존 행의 토큰을 갱신한다.

치지직 Access Token은 1일, Refresh Token은 30일 유효하고 Refresh Token은 1회용이다. 갱신에 성공하는 즉시 이전 토큰이 무효화되므로 서버는 만료 임박 시점에만 갱신한다. 갱신이 불가능하면 연동 상태를 `REAUTH_REQUIRED`로 바꾸고 `409 PLATFORM_REAUTH_REQUIRED`를 반환하며, 사용자는 동일 authorize API로 재연동한다.

---

## 8. 일정 캘린더와 공지

### 8.1 기간별 일정 목록

`GET /calendar-events?from=2026-07-01T00:00:00Z&to=2026-08-01T00:00:00Z`

월간·주간 캘린더에 표시할 일정을 조회한다. `from`, `to`는 필수이며 조회 범위는 최대 62일이다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "calendarEventId": 44,
        "platformAccountId": 15,
        "eventType": "BROADCAST",
        "title": "발로란트 랭크 정복 Day 12",
        "memo": "Day 11 이어서 불멸 티어 도전",
        "startAt": "2026-07-20T12:00:00Z",
        "endAt": "2026-07-20T15:00:00Z",
        "status": "SCHEDULED"
      },
      {
        "calendarEventId": 45,
        "platformAccountId": null,
        "eventType": "DAY_OFF",
        "title": "휴방",
        "memo": null,
        "startAt": "2026-07-24T00:00:00Z",
        "endAt": "2026-07-25T00:00:00Z",
        "status": "SCHEDULED"
      }
    ]
  }
}
```

- `eventType`은 `BROADCAST`, `DAY_OFF` 중 하나다.
- `status`는 `SCHEDULED`, `COMPLETED`, `CANCELLED` 중 하나다.
- `DAY_OFF` 일정은 `platformAccountId = null`을 허용한다.

### 8.2 일정 생성

`POST /calendar-events`

```json
{
  "platformAccountId": 15,
  "eventType": "BROADCAST",
  "title": "발로란트 랭크 정복 Day 12",
  "memo": "Day 11 이어서 불멸 티어 도전. 초반 채팅 이벤트 예정.",
  "startAt": "2026-07-20T12:00:00Z",
  "endAt": "2026-07-20T15:00:00Z"
}
```

성공 시 생성된 `CALENDAR_EVENTS`를 `201 Created`로 반환한다. `BROADCAST` 일정의 `platformAccountId`는 로그인한 스트리머 소유이며 `CONNECTED` 상태여야 한다.

### 8.3 일정 상세·수정·취소

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/calendar-events/{calendarEventId}` | 일정 상세 조회 |
| PATCH | `/calendar-events/{calendarEventId}` | 일정 필드 수정 또는 취소 |

수정 가능 필드는 `platformAccountId`, `eventType`, `title`, `memo`, `startAt`, `endAt`, `status`다.

```json
{
  "title": "발로란트 랭크 정복 Day 12 - 시간 변경",
  "startAt": "2026-07-20T13:00:00Z",
  "endAt": "2026-07-20T16:00:00Z"
}
```

- 취소는 `status = CANCELLED`로 변경해 처리한다.
- `endAt`은 `startAt`보다 늦어야 한다.
- 이미 `BROADCAST_SESSIONS.calendar_event_id`로 연결된 일정은 시간·플랫폼·유형을 변경할 수 없다.

### 8.4 방송 공지 초안 생성·수정·게시

공지 초안 생성:

`POST /calendar-events/{calendarEventId}/announcement-drafts`

```json
{
  "tone": "FRIENDLY"
}
```

```json
{
  "success": true,
  "data": {
    "announcementId": 81,
    "calendarEventId": 44,
    "content": "오늘 밤 9시! 발로란트 랭크 정복 Day 12 갑니다.",
    "aiGenerated": true,
    "publishStatus": "DRAFT",
    "publishedAt": null
  }
}
```

초안 수정과 게시:

| Method | Endpoint | 설명 |
| --- | --- | --- |
| PATCH | `/schedule-announcements/{announcementId}` | `content` 수정 |
| POST | `/schedule-announcements/{announcementId}/publish` | 연결된 플랫폼 계정에 게시 |

게시 성공 시 `publishStatus = PUBLISHED`와 `publishedAt`을 반환한다. 게시 대상은 `CALENDAR_EVENTS.platform_account_id`로 정하며, `DAY_OFF` 또는 플랫폼 계정이 없는 일정은 게시할 수 없다. 목업의 “복사”는 클라이언트 Clipboard 기능이므로 API가 필요하지 않다.

---

## 9. 대시보드

### 9.1 대시보드 조회

`GET /dashboard`

목업의 다음 방송, 최근 세션 성과, 미이행 약속, 주간 지표, 최근 하이라이트를 한 번에 반환한다.

```json
{
  "success": true,
  "data": {
    "upcomingBroadcast": {
      "calendarEventId": 44,
      "title": "발로란트 랭크 정복 Day 12",
      "startAt": "2026-07-21T12:00:00Z",
      "endAt": "2026-07-21T15:00:00Z",
      "status": "SCHEDULED"
    },
    "recentSession": {
      "sessionId": 9001,
      "title": "심야 수다방 & 롤토체스",
      "endedAt": "2026-07-20T16:32:00Z",
      "reportStatus": "COMPLETED",
      "avgViewers": 2105,
      "peakViewers": 3842,
      "summary": "롤토체스 구간에서 시청자 유입이 집중되었습니다."
    },
    "pendingPromiseCount": 5,
    "pendingPromises": [
      {
        "promiseId": 31,
        "sessionId": 9001,
        "content": "구독자 500명 달성 시 게임 추가",
        "status": "PENDING",
        "dueDate": "2026-07-26",
        "detectedAt": "2026-07-20T14:43:00Z"
      }
    ],
    "weeklyMetrics": {
      "periodStart": "2026-07-14",
      "periodEnd": "2026-07-20",
      "avgViewers": 2138,
      "avgViewersChangeRate": 0.12,
      "totalChat": 48200,
      "totalChatChangeRate": 0.08
    },
    "recentHighlights": [
      {
        "highlightId": 71,
        "durationMs": 42000,
        "clipUrl": "https://cdn.example.com/clips/71.mp4",
        "thumbnailUrl": "https://cdn.example.com/clips/71.jpg"
      }
    ]
  }
}
```

**제외 필드**

- `notifications`
- `reportReadyBanner`
- `unreadNotificationCount`

리포트 생성 상태는 최근 세션 카드의 `reportStatus`로만 제공하며 별도 상단 알림을 만들지 않는다.

`pendingPromises`는 `PROMISES.status`가 `PENDING` 또는 `ON_HOLD`인 미이행 약속 중 기한이 가까운 최대 3건만 반환한다. 전체 건수는 `pendingPromiseCount`이며, 목업의 “더 보기”는 `GET /promises`로 이동한다. 따라서 대시보드 API에는 약속 요약이 이미 포함되어 있지만 전체 목록과 감지 약속 확정 기능은 포함하지 않는다.

---

## 10. 방송 세션과 실시간 콘솔

### 10.1 방송 세션 목록

`GET /broadcast-sessions?page=0&size=20&status=ENDED`

응답 항목:

| 필드 | 설명 |
| --- | --- |
| `sessionId` | `BROADCAST_SESSIONS.id` |
| `title`, `category`, `thumbnailUrl` | 방송 기본 정보 |
| `status` | `PREPARING`, `LIVE`, `ENDED`, `FAILED` |
| `startedAt`, `endedAt` | 방송 시각 |
| `reportStatus` | 연결된 `REPORTS.status` |

### 10.2 방송 분석 세션 시작

`POST /broadcast-sessions`

```json
{
  "platformAccountId": 15,
  "calendarEventId": 44,
  "title": "발로란트 랭크 정복 Day 12",
  "category": "FPS",
  "language": "ko"
}
```

```json
{
  "success": true,
  "data": {
    "sessionId": 9002,
    "status": "PREPARING",
    "startedAt": "2026-07-21T12:00:00Z"
  }
}
```

한 스트리머는 동시에 하나의 `LIVE` 세션만 가질 수 있다.

### 10.3 진행 중 세션

`GET /broadcast-sessions/current`

- 진행 중 세션이 있으면 `200 OK`
- 진행 중 세션이 없으면 `204 No Content`

### 10.4 방송 세션 상세

`GET /broadcast-sessions/{sessionId}`

```json
{
  "success": true,
  "data": {
    "sessionId": 9001,
    "platformAccountId": 15,
    "calendarEventId": 44,
    "title": "심야 수다방 & 롤토체스",
    "category": "VARIETY",
    "language": "ko",
    "thumbnailUrl": "https://cdn.example.com/sessions/9001.jpg",
    "vodUrl": "https://platform.example/vod/9001",
    "status": "ENDED",
    "startedAt": "2026-07-20T13:04:00Z",
    "endedAt": "2026-07-20T16:32:00Z"
  }
}
```

### 10.5 방송 분석 세션 종료

`POST /broadcast-sessions/{sessionId}/end`

방송 세션을 종료하고 세션 리포트 생성 작업을 등록한다.

```json
{
  "success": true,
  "data": {
    "sessionId": 9002,
    "status": "ENDED",
    "reportStatus": "PROCESSING"
  }
}
```

처리 접수 성공 시 `202 Accepted`를 반환한다.

### 10.6 실시간 콘솔 스냅샷

`GET /broadcast-sessions/{sessionId}/live-snapshot`

새로고침과 WebSocket 재연결 후 화면을 복구할 때 사용한다.

```json
{
  "success": true,
  "data": {
    "session": {
      "sessionId": 9002,
      "status": "LIVE",
      "startedAt": "2026-07-21T12:00:00Z",
      "elapsedSeconds": 6452,
      "currentViewers": 2417
    },
    "services": {
      "microphone": "CONNECTED",
      "stt": "RUNNING",
      "ai": "RUNNING"
    },
    "briefings": [
      {
        "briefingId": 501,
        "briefingType": "RISK",
        "message": "정치·비방 소지가 있는 표현이 감지되었습니다.",
        "createdAt": "2026-07-21T13:47:00Z"
      }
    ],
    "sentiment": {
      "positiveRatio": 0.64,
      "neutralRatio": 0.27,
      "negativeRatio": 0.09
    },
    "repeatedQuestions": [
      {
        "questionId": 81,
        "question": "오늘 몇 시까지 하세요?",
        "occurrenceCount": 42
      }
    ],
    "lastEventId": "1753089600123-0"
  }
}
```

`briefings[].createdAt`은 `LIVE_BRIEFINGS.created_at`을 반환한다. `created_at`은 `NOT NULL`이며 브리핑 레코드가 생성된 시각이다.

`lastEventId`는 해당 세션 Redis Stream의 최신 이벤트 ID다. 아직 생성된 이벤트가 없으면 `"0-0"`을 반환한다.

스냅샷에는 `moderationQueue` 또는 모더레이션 관련 필드를 포함하지 않는다.

### 10.7 WebSocket 연결

티켓 발급:

`POST /broadcast-sessions/{sessionId}/ws-ticket`

연결 주소:

```
wss://api.example.com/ws/v1/broadcast-sessions/{sessionId}?ticket={wsTicket}&lastEventId={lastEventId}
```

`lastEventId`는 선택값이다. 최초 연결에서는 생략하고, 재연결 시 클라이언트가 마지막으로 정상 처리한 Redis Stream ID를 전달한다.

공통 이벤트:

```json
{
  "eventId": "1753089600456-0",
  "type": "BRIEFING_CREATED",
  "sessionId": 9002,
  "occurredAt": "2026-07-21T13:47:00Z",
  "data": {}
}
```

| 이벤트 | 설명 |
| --- | --- |
| `SESSION_STATUS_CHANGED` | 방송 상태 변경 |
| `VIEWER_METRIC_UPDATED` | 현재 시청자 수 갱신 |
| `BRIEFING_CREATED` | 위험 발언, 채팅 요약, 반복 질문, 약속 브리핑 |
| `SENTIMENT_UPDATED` | 긍정·중립·부정 비율 갱신 |
| `REPEATED_QUESTIONS_UPDATED` | 반복 질문 Top 5 갱신 |
| `ANALYSIS_STATUS_CHANGED` | 방송 종료 후 리포트 상태 변경 |
| `ERROR` | 실시간 처리 오류 |

#### Redis Streams 기반 재연결 복구

- 세션별 Stream Key는 `ws:session:{sessionId}:events`를 사용한다.
- `eventId`는 Redis Stream ID이며 클라이언트의 중복 적용 방지 키로도 사용한다.
- `BRIEFING_CREATED`, `SESSION_STATUS_CHANGED`, `ANALYSIS_STATUS_CHANGED`처럼 놓치면 안 되는 이벤트만 Stream에 저장하고 재생한다.
- `VIEWER_METRIC_UPDATED`, `SENTIMENT_UPDATED`, `REPEATED_QUESTIONS_UPDATED`는 현재 상태가 중요하므로 과거 이벤트를 전부 재생하지 않고 `live-snapshot`으로 복구한다.
- 재연결 시 서버는 `XREAD`로 `lastEventId` 이후의 이벤트를 순서대로 전송한 뒤 실시간 이벤트 전송을 이어간다.
- Stream이 만료되었거나 `lastEventId`가 보관 범위보다 오래된 경우 서버는 `RESYNC_REQUIRED` 이벤트를 전송하고 클라이언트는 `live-snapshot`을 다시 조회한다.
- Stream은 세션별 최대 1,000건을 유지하고 방송 종료 30분 후 만료한다. 방송 중에는 이벤트 추가 시 TTL을 갱신한다.

`BRIEFING_CREATED.data.briefingType`:

- `RISK`
- `CHAT_SUMMARY`
- `FAQ`
- `PROMISE`

`MODERATION` 유형은 정의하지 않는다.

### 10.8 약속 관리

AI가 약속 발언을 감지하면 `PROMISES`에 `DETECTED` 상태로 저장하고, `BRIEFING_CREATED.data.promiseId`로 식별자를 전달한다. 사용자가 목업의 “약속 추가”를 누르면 확정하고 “무시”를 누르면 제외한다.

#### 10.8.1 약속 전체 목록

`GET /promises?page=0&size=20&status=PENDING`

`status`는 선택값이며 생략하면 `DETECTED`, `PENDING`, `ON_HOLD`, `COMPLETED`, `DISMISSED` 전체를 조회한다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "promiseId": 31,
        "sessionId": 9001,
        "transcriptId": 701,
        "content": "구독자 500명 달성 시 게임 추가",
        "status": "PENDING",
        "dueDate": "2026-07-26",
        "detectedAt": "2026-07-20T14:43:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

`detectedAt`은 별도 컬럼을 추가하지 않고 `BROADCAST_SESSIONS.started_at + TRANSCRIPTS.start_ms`로 계산한다.

#### 10.8.2 감지 약속 확정·무시

확정:

`POST /promises/{promiseId}/confirm`

```json
{
  "content": "구독자 추첨 이벤트 진행",
  "dueDate": "2026-07-27"
}
```

확정 시 사용자가 수정한 `content`, `due_date`를 반영하고 `status`를 `DETECTED`에서 `PENDING`으로 변경한다. 성공 응답은 변경된 약속을 반환한다.

무시:

`POST /promises/{promiseId}/dismiss`

무시 시 `status`를 `DISMISSED`로 변경하고 `204 No Content`를 반환한다. 두 API 모두 현재 상태가 `DETECTED`가 아니면 `409 Conflict`를 반환한다.

#### 10.8.3 확정 약속 상태 변경

`PATCH /promises/{promiseId}`

```json
{
  "status": "COMPLETED"
}
```

변경 가능한 `status`는 `PENDING`, `COMPLETED`, `ON_HOLD` 중 하나다. `DETECTED`, `DISMISSED` 상태는 확정·무시 전용 API로만 변경한다.

---

## 11. 리포트와 하이라이트

### 11.1 세션 리포트

`GET /broadcast-sessions/{sessionId}/report`

목업의 헤더, 시청자 추이, AI 요약, 개선 제안, 하이라이트 후보를 한 응답으로 반환한다.

```json
{
  "success": true,
  "data": {
    "reportId": 301,
    "status": "COMPLETED",
    "session": {
      "sessionId": 9001,
      "title": "심야 수다방 & 롤토체스",
      "startedAt": "2026-07-20T13:04:00Z",
      "endedAt": "2026-07-20T16:32:00Z"
    },
    "summary": "롤토체스 구간에서 시청자 유입이 집중되었습니다.",
    "metrics": {
      "avgViewers": 2105,
      "peakViewers": 3842,
      "totalChat": 48200,
      "positiveRatio": 0.64,
      "neutralRatio": 0.27,
      "negativeRatio": 0.09
    },
    "viewerTimeline": [
      {
        "measuredAt": "2026-07-20T13:05:00Z",
        "viewerCount": 1820
      }
    ],
    "segments": [
      {
        "reportSegmentId": 401,
        "transcriptId": 701,
        "startMs": 2760000,
        "endMs": 5760000,
        "segmentType": "VIEWER_INFLOW"
      }
    ],
    "insights": [
      {
        "insightId": 501,
        "title": "자정 게임 전환을 미리 예고하기",
        "content": "전환 시점에 시청자 이탈이 발생했습니다."
      }
    ],
    "highlights": [
      {
        "highlightId": 71,
        "reportSegmentId": 401,
        "startMs": 5220000,
        "endMs": 5261000,
        "clipUrl": "https://cdn.example.com/clips/71.mp4",
        "thumbnailUrl": "https://cdn.example.com/clips/71.jpg"
      }
    ],
    "generatedAt": "2026-07-20T16:35:00Z"
  }
}
```

- `PROCESSING`이면 `202 Accepted`와 진행 상태만 반환한다.
- AI 요약 실패 시에도 집계 지표는 `PARTIAL` 상태로 반환한다.
- 목업의 하이라이트 점수와 사유는 현재 ERD에 저장 컬럼이 없으므로 응답에서 제외한다.

### 11.2 성장 리포트

`GET /growth-reports?periodType=WEEKLY&from=2026-05-25&to=2026-07-20`

```json
{
  "success": true,
  "data": {
    "periodType": "WEEKLY",
    "from": "2026-05-25",
    "to": "2026-07-20",
    "summaryMetrics": {
      "avgViewers": 2138,
      "avgViewersChangeRate": 0.18,
      "totalChat": 48200,
      "totalChatChangeRate": 0.09,
      "broadcastMinutes": 1290,
      "broadcastMinutesChangeRate": -0.04
    },
    "series": [
      {
        "periodStart": "2026-07-14",
        "periodEnd": "2026-07-20",
        "avgViewers": 2138,
        "totalChat": 48200,
        "broadcastMinutes": 1290
      }
    ]
  }
}
```

`periodType`은 `WEEKLY` 또는 `MONTHLY`다. 성장 목표와 AI 다음 액션은 별도 Growth Goal API로 조회한다.

### 11.3 성장 목표 조회·수정

목표와 AI 다음 액션 조회:

`GET /growth-goals?status=ACTIVE`

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "growthGoalId": 21,
        "metricType": "AVG_VIEWERS",
        "targetValue": 2500,
        "unit": "VIEWERS",
        "periodStart": "2026-07-01",
        "periodEnd": "2026-09-30",
        "status": "ACTIVE",
        "currentValue": 2138,
        "progressRate": 0.8552,
        "actions": [
          {
            "growthActionId": 91,
            "title": "롤토체스 편성 확대",
            "content": "롤토체스 편성을 주 2회로 늘리세요. 최근 6주 평균 유입이 42% 높았습니다."
          }
        ]
      }
    ]
  }
}
```

- `currentValue`, `progressRate`는 `REPORTS`, `BROADCAST_SESSIONS` 집계로 계산하며 DB 컬럼이 아니다.
- `actions`는 `GROWTH_ACTIONS.growth_goal_id`로 연결된 항목이다.
- `metricType`은 `AVG_VIEWERS`, `BROADCASTS_PER_WEEK`, `TOTAL_CHAT`, `BROADCAST_MINUTES` 중 하나다.
- `status`는 `ACTIVE`, `ACHIEVED`, `CANCELLED` 중 하나다.

목표 수정:

`PATCH /growth-goals/{growthGoalId}`

```json
{
  "metricType": "AVG_VIEWERS",
  "targetValue": 3000,
  "unit": "VIEWERS",
  "periodStart": "2026-07-01",
  "periodEnd": "2026-10-31",
  "status": "ACTIVE"
}
```

요청 필드는 모두 선택값이며 전달된 컬럼만 수정한다. `GROWTH_ACTIONS`는 AI가 별도로 생성한 제안이므로 이 API에서 수정하지 않는다.

### 11.4 하이라이트 목록

`GET /broadcast-sessions/{sessionId}/highlights`

반환 필드는 `highlightId`, `reportSegmentId`, `startMs`, `endMs`, `clipUrl`, `thumbnailUrl`이다.

### 11.5 하이라이트 채팅 리플레이

`GET /highlights/{highlightId}/chat-replay?cursor={cursor}&size=200`

```json
{
  "success": true,
  "data": {
    "highlightId": 71,
    "sessionId": 9001,
    "startMs": 5220000,
    "endMs": 5261000,
    "items": [
      {
        "chatMessageId": 88001,
        "viewerAlias": "viewer-a91c",
        "message": "이거 클립 박제각",
        "sentAt": "2026-07-20T14:31:03Z",
        "offsetMs": 3000
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```

- 조회 범위는 `BROADCAST_SESSIONS.started_at + HIGHLIGHTS.start_ms` 이상, `started_at + end_ms` 미만인 `CHAT_MESSAGES.sent_at`이다.
- `offsetMs`는 하이라이트 시작점을 0으로 한 계산 필드이며 플레이어와 채팅 동기화에 사용한다.
- 정렬은 `CHAT_MESSAGES.sent_at ASC, CHAT_MESSAGES.id ASC`이고 `size`의 최댓값은 500이다.
- `cursor`는 서버가 마지막 정렬 키를 인코딩해 발급하며 클라이언트가 직접 생성하지 않는다.
- ERD의 `VIEWER_PROFILES`에는 닉네임 컬럼이 없으므로 실제 플랫폼 닉네임 대신 `viewer_hash`로부터 세션 내에서 안정적인 `viewerAlias`를 계산한다. 실제 닉네임이 필요하면 ERD에 별도 컬럼을 추가해야 한다.
- 목업의 후원 메시지는 현재 ERD에 후원 이벤트 테이블이 없으므로 채팅 리플레이 응답에서 제외한다.

---

## 12. 포트폴리오

### 12.1 내 포트폴리오 조회

`GET /portfolio/me`

```json
{
  "success": true,
  "data": {
    "portfolioId": 61,
    "publicSlug": "hanbyeol",
    "title": "한별 TV",
    "introduction": "신작과 도전형 콘텐츠를 중심으로 방송합니다.",
    "contactEmail": "business@example.com",
    "contactUrl": "https://example.com/contact",
    "isPublic": true,
    "publishedAt": "2026-07-20T08:00:00Z",
    "profile": {
      "nickname": "한별TV",
      "profileImageUrl": "https://cdn.example.com/profile/101.jpg",
      "platformAccounts": [
        {
          "platformAccountId": 15,
          "platform": "CHZZK",
          "channelId": "ch_12345",
          "channelName": "한별TV"
        }
      ]
    },
    "tags": [
      { "tagId": 1, "name": "FPS", "tagType": "CONTENT" },
      { "tagId": 2, "name": "롤토체스", "tagType": "CONTENT" }
    ],
    "performance": {
      "avgViewers": 2138,
      "peakViewers": 3842,
      "broadcastsPerWeek": 4,
      "totalBroadcastCount": 142,
      "totalChat": 524300,
      "primaryTimeRange": "21:00-01:00"
    },
    "featuredHighlights": [
      {
        "highlightId": 71,
        "displayOrder": 1,
        "clipUrl": "https://cdn.example.com/clips/71.mp4",
        "thumbnailUrl": "https://cdn.example.com/clips/71.jpg"
      }
    ]
  }
}
```

`primaryTimeRange`는 최근 방송의 `started_at`, `ended_at`을 집계한 계산 필드다.

### 12.2 내 포트폴리오 수정

`PATCH /portfolio/me`

```json
{
  "title": "한별 TV",
  "introduction": "신작과 도전형 콘텐츠를 중심으로 방송합니다.",
  "contactEmail": "business@example.com",
  "contactUrl": "https://example.com/contact",
  "isPublic": true,
  "featuredHighlights": [
    { "highlightId": 71, "displayOrder": 1 },
    { "highlightId": 72, "displayOrder": 2 }
  ]
}
```

수정 시 `PORTFOLIOS`와 `PORTFOLIO_HIGHLIGHTS`를 한 트랜잭션으로 반영한다.

### 12.3 다른 스트리머 포트폴리오 검색

`GET /portfolios`

SC-11의 채널명·태그 검색, 카테고리 필터, 규모 필터를 지원한다.

| Query | 필수 | 설명 |
| --- | --- | --- |
| `keyword` | N | 연결된 플랫폼 계정 중 하나의 채널명 또는 태그 검색 |
| `platform` | N | `CHZZK`, `SOOP`. 연결된 계정 중 하나라도 일치하면 포함 |
| `tag` | N | 태그명 |
| `minAvgViewers` | N | 최소 평균 시청자 |
| `maxAvgViewers` | N | 최대 평균 시청자 |
| `sort` | N | `RECENT`, `AVG_VIEWERS_DESC`, `BROADCASTS_DESC` |
| `page`, `size` | N | 기본 0, 20 |

검색 조건:

- `PORTFOLIOS.is_public = true`
- 로그인한 스트리머 자신의 포트폴리오는 제외
- 추천 점수나 매칭 적합도는 계산하지 않음

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "portfolioId": 61,
        "publicSlug": "hanbyeol",
        "nickname": "한별TV",
        "profileImageUrl": "https://cdn.example.com/profile/101.jpg",
        "platformAccounts": [
          {
            "platformAccountId": 15,
            "platform": "CHZZK",
            "channelId": "ch_12345",
            "channelName": "한별TV"
          }
        ],
        "categoryTags": ["FPS", "롤토체스"],
        "avgViewers": 2138,
        "broadcastsPerWeek": 4
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 12.4 공개 포트폴리오 상세

`GET /portfolios/{publicSlug}`

- 인증 없이 조회 가능
- 비공개 포트폴리오는 `404 Not Found`
- 채팅 원문, 수익, 이메일 로그인 정보, 플랫폼 토큰은 반환하지 않음
- 응답 구조는 내 포트폴리오와 같지만 `isPublic` 등 편집용 상태는 제외

---

## 13. 내부 수집 및 AI API

외부 클라이언트에는 노출하지 않는다. Spring Boot가 PostgreSQL 영속화를 담당하고 FastAPI AI 서버는 DB에 직접 접속하지 않는 무상태 분석 서버로 둔다.

### 13.1 호출 방향과 공통 규약

| 방향 | 역할 |
| --- | --- |
| 수집기·음성 중계 → Spring Boot | 채팅, 시청자 지표, 음성 청크 메타데이터 저장·큐 등록 |
| Spring Boot → FastAPI | STT와 각종 AI 분석 요청 |
| FastAPI → Spring Boot | 비동기 분석 결과 Callback 또는 배치 결과 저장 |
- 내부 인증: `Authorization: Bearer {serviceToken}`
- 모든 변경 요청은 `X-Request-Id`를 필수로 보내며 동일 ID 재시도는 중복 저장하지 않는다.
- 서비스 간 요청과 응답도 공통 `success`, `data`, `error` Envelope을 사용한다.
- FastAPI는 오디오·채팅·STT 원문을 로그에 남기지 않는다.
- `platform-events`, `chat-insights`처럼 여러 데이터 유형을 섞은 기존 추상 Endpoint 대신 아래의 명시적 Endpoint를 사용한다.

### 13.2 내부 API 목록

| 방향 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 수집기 → Spring | POST | `/internal/v1/sessions/{sessionId}/chat-messages:batch` | 채팅 메시지 배치 저장 |
| 수집기 → Spring | POST | `/internal/v1/sessions/{sessionId}/viewer-metrics:batch` | 시청자 수 지표 배치 저장 |
| 음성 중계 → Spring | POST | `/internal/v1/sessions/{sessionId}/audio-chunks` | 음성 청크 저장·STT 작업 등록 |
| FastAPI → Spring | POST | `/internal/v1/sessions/{sessionId}/transcripts:batch` | STT 결과 배치 저장 |
| Spring → FastAPI | POST | `/internal/v1/ai/stt` | 음성 청크 STT 변환 |
| Spring → FastAPI | POST | `/internal/v1/ai/risk-speech` | 위험 발언 분류와 조언 생성 |
| Spring → FastAPI | POST | `/internal/v1/ai/chat-summary` | 채팅 요약과 감정 비율 분석 |
| Spring → FastAPI | POST | `/internal/v1/ai/repeated-questions` | 반복 질문 추출 |
| Spring → FastAPI | POST | `/internal/v1/ai/promise-detection` | STT에서 약속 후보 추출 |
| Spring → FastAPI | POST | `/internal/v1/ai/session-reports` | 세션 리포트 비동기 생성 요청 |
| Spring → FastAPI | POST | `/internal/v1/ai/highlight-detection` | 하이라이트 구간 탐지 |
| Spring → FastAPI | POST | `/internal/v1/ai/growth-actions` | 성장 목표의 다음 액션 제안 |
| FastAPI → Spring | PUT | `/internal/v1/analysis-results/{reportId}` | 리포트·세그먼트·인사이트 Callback 저장 |

### 13.3 원천 데이터 수집과 영속화

#### 13.3.1 채팅 메시지 배치 저장

`POST /internal/v1/sessions/{sessionId}/chat-messages:batch`

```json
{
  "items": [
    {
      "viewerHash": "sha256:a91c...",
      "message": "이거 클립 박제각",
      "sentAt": "2026-07-20T14:31:03Z"
    }
  ]
}
```

```json
{
  "success": true,
  "data": {
    "acceptedCount": 1,
    "duplicateCount": 0
  }
}
```

Spring은 `viewerHash`로 `VIEWER_PROFILES`를 조회하거나 생성한 뒤 `CHAT_MESSAGES(session_id, viewer_id, message, sent_at)`에 저장한다. 플랫폼의 원본 사용자 식별자는 수집기에서 해시하고 이 API로 전달하지 않는다.

#### 13.3.2 시청자 지표 배치 저장

`POST /internal/v1/sessions/{sessionId}/viewer-metrics:batch`

```json
{
  "items": [
    {
      "measuredAt": "2026-07-20T14:31:00Z",
      "viewerCount": 2417
    }
  ]
}
```

각 항목은 `VIEWER_METRICS(session_id, measured_at, viewer_count)`에 저장한다.

#### 13.3.3 음성 청크 수집

`POST /internal/v1/sessions/{sessionId}/audio-chunks`

`multipart/form-data`로 `audio`, `startMs`, `endMs`, `language`를 전달한다. Spring은 음성을 S3 호환 스토리지에 저장하고 STT 작업을 등록한다.

```json
{
  "success": true,
  "data": {
    "audioChunkId": "chunk_01J2ABC",
    "audioObjectKey": "sessions/9002/audio/chunk_01J2ABC.webm",
    "startMs": 6450000,
    "endMs": 6465000,
    "status": "QUEUED"
  }
}
```

`audioChunkId`, `audioObjectKey`는 작업 큐·오브젝트 스토리지 식별자이며 ERD의 외부 리소스가 아니다.

#### 13.3.4 STT 결과 배치 저장

`POST /internal/v1/sessions/{sessionId}/transcripts:batch`

```json
{
  "items": [
    {
      "startMs": 6450000,
      "endMs": 6465000,
      "transcript": "이번 판 이기면 구독자 추첨 이벤트 할게"
    }
  ]
}
```

각 항목은 `TRANSCRIPTS(session_id, start_ms, end_ms, transcript)`에 저장한다. Spring이 STT 응답을 동기식으로 받은 경우에는 서비스 계층에서 직접 저장하고, 이 Endpoint는 비동기 Callback·재처리 배치에 사용한다.

### 13.4 STT 분석

`POST /internal/v1/ai/stt`

```json
{
  "sessionId": 9002,
  "audioChunkId": "chunk_01J2ABC",
  "audioObjectKey": "sessions/9002/audio/chunk_01J2ABC.webm",
  "startMs": 6450000,
  "endMs": 6465000,
  "language": "ko"
}
```

```json
{
  "success": true,
  "data": {
    "audioChunkId": "chunk_01J2ABC",
    "startMs": 6450000,
    "endMs": 6465000,
    "transcript": "이번 판 이기면 구독자 추첨 이벤트 할게"
  }
}
```

Spring은 성공 결과를 `TRANSCRIPTS`에 저장한 후 생성된 `transcriptId`로 위험 발언·약속 분석을 요청한다.

### 13.5 발언 분석

위험 발언 분석:

`POST /internal/v1/ai/risk-speech`

```json
{
  "sessionId": 9002,
  "transcriptId": 701,
  "transcript": "정치 관련 비방 표현",
  "startMs": 6450000,
  "endMs": 6465000
}
```

```json
{
  "success": true,
  "data": {
    "detected": true,
    "riskLevel": "HIGH",
    "riskCategory": "POLITICS",
    "advice": "화제를 전환하고 단정적인 표현을 피하세요.",
    "briefingMessage": "정치·비방 소지가 있는 표현이 감지되었습니다."
  }
}
```

`detected = true`이면 Spring은 `RISK_EVENTS`를 저장하고 `briefingMessage`로 `LIVE_BRIEFINGS(RISK)`를 생성한다.

약속 후보 분석:

`POST /internal/v1/ai/promise-detection`

```json
{
  "sessionId": 9002,
  "transcriptId": 701,
  "transcript": "이번 판 이기면 구독자 추첨 이벤트 할게",
  "broadcastDate": "2026-07-20"
}
```

```json
{
  "success": true,
  "data": {
    "candidates": [
      {
        "content": "구독자 추첨 이벤트 진행",
        "dueDate": null
      }
    ]
  }
}
```

Spring은 후보마다 `PROMISES(streamer_id, session_id, transcript_id, content, status, due_date)`를 `DETECTED` 상태로 저장하고 `LIVE_BRIEFINGS(PROMISE)`를 생성한다.

### 13.6 채팅 분석

채팅 요약과 감정 분석:

`POST /internal/v1/ai/chat-summary`

```json
{
  "sessionId": 9002,
  "windowStart": "2026-07-20T14:28:00Z",
  "windowEnd": "2026-07-20T14:31:00Z",
  "previousSummary": "신규 챔피언 이야기가 시작됐습니다.",
  "messages": [
    {
      "chatMessageId": 88001,
      "message": "신규 챔피언 바로 구매 가자",
      "sentAt": "2026-07-20T14:30:59Z"
    }
  ]
}
```

```json
{
  "success": true,
  "data": {
    "summary": "신규 챔피언 구매에 대한 긍정 반응이 우세합니다.",
    "positiveRatio": 0.64,
    "neutralRatio": 0.27,
    "negativeRatio": 0.09
  }
}
```

Spring은 `summary`로 `LIVE_BRIEFINGS(CHAT_SUMMARY)`를 생성하고 비율을 `SENTIMENT_SNAPSHOTS`에 저장한다.

반복 질문 분석:

`POST /internal/v1/ai/repeated-questions`

요청의 `messages` 형식은 채팅 요약 API와 동일하다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "question": "오늘 몇 시까지 하세요?",
        "occurrenceCount": 42
      }
    ]
  }
}
```

`REPEATED_QUESTIONS`는 이력 테이블이 아니라 세션의 현재 Top 질문 상태로 사용한다. Spring은 해당 세션의 기존 행을 새 결과로 한 트랜잭션 안에서 교체하고 필요하면 `LIVE_BRIEFINGS(FAQ)`를 생성한다.

### 13.7 방송 후 분석과 성장 액션

세션 리포트 생성:

`POST /internal/v1/ai/session-reports`

```json
{
  "reportId": 301,
  "sessionId": 9001,
  "inputObjectKey": "sessions/9001/analysis/report-input.json"
}
```

대용량 시청자 시계열·채팅·STT 입력은 요청 Body에 직접 넣지 않고 S3 호환 스토리지의 집계 입력 파일로 전달한다. 성공 시 `202 Accepted`와 `analysisJobId`, `PROCESSING` 상태를 반환하며 완료 결과는 `PUT /internal/v1/analysis-results/{reportId}`로 Callback한다.

리포트 결과 Callback:

```json
{
  "status": "COMPLETED",
  "summary": "롤토체스 구간에서 시청자 유입이 집중되었습니다.",
  "segments": [
    {
      "transcriptId": 701,
      "startMs": 2760000,
      "endMs": 5760000,
      "segmentType": "VIEWER_INFLOW"
    }
  ],
  "insights": [
    {
      "title": "게임 전환을 미리 예고하기",
      "content": "전환 시점에 시청자 이탈이 발생했습니다."
    }
  ]
}
```

Spring은 `REPORTS`, `REPORT_SEGMENTS`, `REPORT_INSIGHTS`를 한 트랜잭션으로 저장한다. 통계 컬럼은 Spring이 원천 데이터에서 계산하며 AI Callback 값을 신뢰해 덮어쓰지 않는다.

하이라이트 탐지:

`POST /internal/v1/ai/highlight-detection`

```json
{
  "reportId": 301,
  "sessionId": 9001,
  "segments": [
    {
      "reportSegmentId": 401,
      "startMs": 2760000,
      "endMs": 5760000
    }
  ],
  "viewerMetricsObjectKey": "sessions/9001/analysis/viewer-metrics.json",
  "chatCountsObjectKey": "sessions/9001/analysis/chat-counts.json"
}
```

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "reportSegmentId": 401,
        "startMs": 5220000,
        "endMs": 5261000
      }
    ]
  }
}
```

Spring은 결과를 `HIGHLIGHTS(report_id, report_segment_id, start_ms, end_ms)`에 저장하고 클립 생성 후 `clip_url`, `thumbnail_url`을 갱신한다. 현재 ERD에 없는 점수·탐지 사유는 저장 결과에 포함하지 않는다.

성장 다음 액션 생성:

`POST /internal/v1/ai/growth-actions`

```json
{
  "growthGoalId": 21,
  "metricType": "AVG_VIEWERS",
  "targetValue": 2500,
  "currentValue": 2138,
  "metricsObjectKey": "streamers/101/analysis/growth-input.json"
}
```

```json
{
  "success": true,
  "data": {
    "actions": [
      {
        "title": "롤토체스 편성 확대",
        "content": "최근 6주 유입 근거를 바탕으로 주 2회 편성을 권장합니다."
      }
    ]
  }
}
```

Spring은 기존 활성 제안을 교체하고 `GROWTH_ACTIONS(growth_goal_id, title, content)`에 저장한다.

### 13.8 분석 결과와 ERD 매핑

| 분석 결과 | 저장 테이블 |
| --- | --- |
| STT 전문 | `TRANSCRIPTS` |
| 위험 발언 | `RISK_EVENTS`, `LIVE_BRIEFINGS` |
| 채팅 요약 | `LIVE_BRIEFINGS` |
| 감정 비율 | `SENTIMENT_SNAPSHOTS` |
| 반복 질문 | `REPEATED_QUESTIONS` |
| 약속 후보 | `PROMISES`, `LIVE_BRIEFINGS` |
| 세션 요약·구간·개선안 | `REPORTS`, `REPORT_SEGMENTS`, `REPORT_INSIGHTS` |
| 하이라이트 구간 | `HIGHLIGHTS` |
| 성장 다음 액션 | `GROWTH_ACTIONS` |

모더레이션 분류·자동 조치 API는 이번 범위에서 만들지 않는다.

---

## 14. 권한

| 리소스 | 권한 |
| --- | --- |
| 내 프로필·플랫폼 연동 | 로그인한 본인 |
| 비밀번호 최초 설정·변경 | 로그인한 본인 |
| 비밀번호 재설정 요청·적용 | Public + 유효한 재설정 토큰 |
| 일정·방송 공지 | 일정과 플랫폼 계정 소유 스트리머 |
| 방송 세션·실시간 콘솔 | 세션 소유 스트리머 |
| 약속 조회·확정·상태 변경 | 약속의 `streamer_id`와 로그인 사용자 일치 |
| 세션·성장 리포트 | 리포트 소유 스트리머 |
| 성장 목표 조회·수정 | 목표의 `streamer_id`와 로그인 사용자 일치 |
| 하이라이트 채팅 리플레이 | 하이라이트가 속한 세션 소유 스트리머 |
| 내 포트폴리오 수정 | 포트폴리오 소유 스트리머 |
| 포트폴리오 검색 | 로그인한 스트리머 |
| 공개 포트폴리오 상세 | Public |

ERD가 스트리머 중심으로 수정되었으므로 `ADVERTISER` 역할과 광고주 전용 API는 정의하지 않는다.

---

## 15. 주요 오류 코드

| 코드 | HTTP | 설명 |
| --- | --- | --- |
| `AUTH_INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |
| `AUTH_TOKEN_EXPIRED` | 401 | 인증 토큰 만료 |
| `AUTH_PASSWORD_ALREADY_SET` | 409 | 이미 비밀번호가 설정된 계정 |
| `AUTH_CURRENT_PASSWORD_INVALID` | 400 | 현재 비밀번호 불일치 |
| `AUTH_PASSWORD_POLICY_VIOLATION` | 400 | 새 비밀번호 정책 위반 |
| `AUTH_PASSWORD_RESET_TOKEN_INVALID` | 400 | 재설정 토큰 만료·사용 완료·위조 |
| `AUTH_EMAIL_REQUIRED` | 422 | 비밀번호 설정에 사용할 이메일 없음 |
| `PLATFORM_CONNECTION_FAILED` | 502 | 플랫폼 OAuth 또는 API 실패 |
| `PLATFORM_REAUTH_REQUIRED` | 409 | 플랫폼 토큰 재연동 필요 |
| `CALENDAR_EVENT_NOT_FOUND` | 404 | 일정 없음 |
| `CALENDAR_EVENT_LOCKED` | 409 | 방송 세션에 연결되어 주요 필드를 수정할 수 없는 일정 |
| `ANNOUNCEMENT_NOT_FOUND` | 404 | 방송 공지 없음 |
| `ANNOUNCEMENT_PUBLISH_FAILED` | 502 | 외부 플랫폼 공지 게시 실패 |
| `SESSION_ALREADY_LIVE` | 409 | 이미 진행 중인 방송 존재 |
| `SESSION_NOT_FOUND` | 404 | 방송 세션 없음 |
| `SESSION_ALREADY_ENDED` | 409 | 이미 종료된 방송 |
| `PROMISE_NOT_FOUND` | 404 | 약속 없음 |
| `PROMISE_STATE_CONFLICT` | 409 | 현재 약속 상태에서 확정·무시·상태 변경 불가 |
| `REPORT_FAILED` | 500 | 리포트 생성 최종 실패 |
| `GROWTH_GOAL_NOT_FOUND` | 404 | 성장 목표 없음 |
| `HIGHLIGHT_NOT_FOUND` | 404 | 하이라이트 없음 |
| `PORTFOLIO_NOT_FOUND` | 404 | 포트폴리오 없음 또는 비공개 |
| `PORTFOLIO_HIGHLIGHT_INVALID` | 400 | 본인 소유가 아닌 하이라이트 지정 |
| `INTERNAL_AUTH_FAILED` | 401 | 서비스 간 인증 실패 |
| `AUDIO_CHUNK_INVALID` | 400 | 지원하지 않는 형식·길이의 음성 청크 |
| `AI_STT_FAILED` | 502 | STT 처리 실패 |
| `AI_ANALYSIS_FAILED` | 502 | 위험 발언·채팅·리포트 등 AI 분석 실패 |
| `AI_RESULT_CONFLICT` | 409 | 완료·폐기된 작업에 상충하는 결과 Callback |

---

## 16. 구현 전 확인 사항

- [ ]  치지직·SOOP API에서 방송 상태, 채팅, 시청자 수, VOD URL 제공 가능 여부
- [ ]  치지직·SOOP API의 일정 공지 게시 지원 여부와 권한 범위
- [ ]  1.4의 Unique 제약과 1.5의 Nullable 규칙을 실제 DDL·마이그레이션에 반영
- [ ]  `CALENDAR_EVENTS.event_type/status`, `SCHEDULE_ANNOUNCEMENTS.publish_status` Enum을 실제 DDL에 반영
- [ ]  `PROMISES.status`에 `DETECTED`, `PENDING`, `ON_HOLD`, `COMPLETED`, `DISMISSED` 반영
- [ ]  `GROWTH_GOALS.metric_type/status` Enum과 단위 문자열 정책 확정
- [ ]  소셜 제공자 이메일과 기존 이메일 계정의 안전한 연결 정책
- [ ]  비밀번호 재설정 메일 발송 서비스와 Redis TTL 설정
- [ ]  Electron 앱의 음성 수집 방식과 권한
- [ ]  Spring·FastAPI 서비스 토큰 회전, 네트워크 접근 제한 또는 mTLS 적용
- [ ]  `X-Request-Id` 멱등 처리 Redis TTL과 동일 요청 판별 범위 확정
- [ ]  채팅·시청자 지표·STT 배치의 최대 항목 수와 요청 크기 제한
- [ ]  실시간 STT·위험 발언·채팅 분석의 Timeout, 재시도, Circuit Breaker 정책
- [ ]  리포트 입력 JSON과 음성 청크의 S3 Object Key 규칙·보관 기간·삭제 작업
- [ ]  목업에 표시된 하이라이트 점수·탐지 사유가 필요하면 `HIGHLIGHTS` 컬럼 추가
- [ ]  실제 채팅 닉네임이 필요하면 `VIEWER_PROFILES`에 공개용 표시명 저장 정책과 컬럼 추가 검토
- [ ]  목업의 후원 채팅 리플레이가 필요하면 후원 이벤트 테이블 추가 검토
- [ ]  포트폴리오의 구독자 수를 표시하려면 플랫폼 조회 방식 또는 저장 컬럼 추가
- [ ]  포트폴리오 연락처 공개 동의 정책
- [ ]  음성, 채팅, STT 전문의 보관 기간
- [ ]  Redis Streams의 세션별 최대 길이, 방송 중 TTL 갱신, 종료 후 30분 보관 정책 부하 테스트
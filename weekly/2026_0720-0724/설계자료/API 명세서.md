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
| `TRIGGERS` | `(session_id, start_ms)` | 리포트 재생성 시 동일 트리거 중복 생성 방지 |
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
| `REPORTS`의 8종 감정 건수와 `analyzed_chat` | 집계가 아직 완료되지 않은 `PROCESSING` 상태 |
| `REPORTS.generated_at` | 리포트 생성이 완료되지 않은 상태 |
| `CHAT_MESSAGES.sentiment_label` | 채팅 분류 호출이 실패해 라벨 없이 원본만 저장한 채팅 (13.6) |
| `TRIGGERS.transcript_id` | 트리거 구간에 겹치는 STT 청크가 없는 경우 |
| `TRIGGERS.narrative`, `analyzed_at` | 리포트 생성 전이거나 LLM 분석이 실패한 트리거 |
| `TRIGGERS.screen_context` | 화면 캡처 도입 전. 현재 전 구간 `NULL` |
| `PROMISES.due_date` | 목업의 “기한 미정” 약속 |
| `HIGHLIGHTS.clip_url`, `thumbnail_url` | 하이라이트 구간 탐지 후 클립 생성 전 또는 생성 실패 상태 |
| `PORTFOLIOS.published_at` | 아직 공개하지 않은 포트폴리오 |

### 1.6 구현 제약과 ERD 외 상태

- 한 스트리머가 동시에 하나의 `LIVE` 세션만 갖도록 방송 시작 시 DB 트랜잭션 잠금과 Redis 세션 잠금을 함께 사용한다.
- 스트리머는 여러 `PLATFORM_ACCOUNTS`를 가질 수 있으므로 포트폴리오 API는 플랫폼 정보를 항상 `platformAccounts` 배열로 반환한다.
- 실시간 현재값은 DB·Redis에서 스냅샷으로 복구하고, 놓치면 안 되는 브리핑·세션 상태 이벤트만 Redis Streams에서 재생한다.
- Redis 이벤트 보관 범위를 초과한 경우에는 이벤트 전체 재생 대신 `GET /broadcast-sessions/{sessionId}/live-snapshot`을 다시 호출한다.
- 내부 AI 변경 요청의 `X-Request-Id` 처리 결과는 Redis에 단기 보관해 재시도에 의한 중복 저장을 방지한다.
- 채팅 감정은 **8종 라벨을 원본 그대로** 저장하고 화면 표현은 조회 시점에 만든다. 프론트는 건수 상위 3종만 노출하므로 긍정·중립·부정 3분류를 DB에 두지 않는다. 3분류로 눌러 저장하면 `놀람`·`혼란/질문`을 어디에 넣을지가 자의적인 데다, 나중에 분류 기준을 바꿨을 때 과거 리포트와 추이를 비교할 수 없다.
- 감정은 비율이 아니라 **건수**로 저장한다(13.7). 비율은 조회 시점에 `건수 ÷ analyzed_chat`으로 계산하므로 반올림 잔차가 생기지 않고, 여러 세션을 묶는 성장 리포트에서도 채팅량 가중이 자동으로 걸린다.
- 8종 라벨은 AI 서버가 한국어 문자열로 주고 DB에는 ASCII 코드로 저장한다. 변환은 13.6의 라벨 코드 표를 따르며 채팅 분류 응답을 저장하는 한 지점에서만 수행한다.
- ERD의 `SENTIMENT_SNAPSHOTS`는 사용하지 않는다. 세션 단위 분포는 `REPORTS`, 구간 단위 분포는 `TRIGGERS`, 방송 중 현재값은 채팅 AI 응답을 그대로 중계하고 재연결 시 Redis 스냅샷으로 복구한다. 건별 라벨이 `CHAT_MESSAGES`에 있으므로 윈도우 요약본을 따로 보관할 이유가 없다.
- ERD의 `REPORT_SEGMENTS`는 `TRIGGERS`로 대체한다. 사유는 13.7에 적는다.

---

## 2. 화면별 API

| 화면 | API |
| --- | --- |
| SC-01 로그인 / 플랫폼 연동 | 이메일·소셜 Auth API, Password API, Platform Account API |
| SC-02 대시보드 | `GET /dashboard`, `GET /promises` |
| SC-03 방송 콘솔 | Broadcast Session API, Live Snapshot 폴링(2~3초 주기), 약속 확정·무시 API |
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
| 실시간 콘솔 | `BROADCAST_SESSIONS`, `VIEWER_METRICS`, `CHAT_MESSAGES`, `LIVE_BRIEFINGS`, `RISK_EVENTS`, `REPEATED_QUESTIONS`, `TRIGGERS`, Redis Streams 이벤트 버퍼 |
| 약속 | `PROMISES`, `TRANSCRIPTS`, `BROADCAST_SESSIONS` |
| 세션 분석 | `TRANSCRIPTS`, `CHAT_MESSAGES`, `TRIGGERS`, `REPORTS`, `REPORT_INSIGHTS`, `HIGHLIGHTS` |
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
| Live | GET | `/broadcast-sessions/{sessionId}/live-snapshot` | 콘솔 실시간 상태 (프론트가 2~3초 폴링) |
| Live | POST | `/broadcast-sessions/{sessionId}/ws-ticket` | WebSocket 티켓 발급(보류 — 10.7 참조) |
| Promise | GET | `/promises` | 약속 전체 목록 |
| Promise | POST | `/promises/{promiseId}/confirm` | 감지 약속 확정 |
| Promise | POST | `/promises/{promiseId}/dismiss` | 감지 약속 무시 |
| Promise | PATCH | `/promises/{promiseId}` | 확정 약속 상태 변경 |
| Report | GET | `/broadcast-sessions/{sessionId}/report` | 세션 리포트 조회 |
| Report | GET | `/growth-reports` | 성장 지표 조회 |
| Growth Goal | GET | `/growth-goals` | 성장 목표와 다음 액션 조회 |
| Growth Goal | POST | `/growth-goals` | 성장 목표 생성 |
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

> **2026-07-29 개정.** 실시간 콘솔의 전달 방식을 WebSocket 푸시에서 **이 스냅샷의 폴링(2~3초 주기)**으로 바꾼다. 콘솔은 클라이언트→서버 메시지가 없고 접속자가 스트리머 본인 창 2~3개뿐이라, 티켓·재연결 재생 배관의 비용이 푸시로 얻는 지연 단축을 넘는다. 원래 설계에서도 여론·시청자 수는 재생 없이 스냅샷 복구가 정본이었으므로 폴링은 그 부분집합이다. WebSocket(10.7)은 보류로 남긴다.

프론트는 콘솔 진입 시부터 이 응답 하나로 화면 전체를 그리고, 새로고침·재입장 복구도 같은 호출로 끝난다.

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
      "ai": "RUNNING",
      "collector": "RUNNING"
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
      "windowSec": 180,
      "analyzedChat": 6185,
      "engagement": 0.214,
      "dominant": "JOY",
      "distribution": {
        "JOY": 0.27, "NEUTRAL": 0.25, "SUPPORT": 0.13, "EXCITEMENT": 0.11,
        "CONFUSION": 0.09, "SURPRISE": 0.06, "SADNESS": 0.05, "ANGER": 0.04
      }
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

`services`의 각 필드 판정 기준 (2026-07-29 구현 기준):

- `microphone`, `stt` — 음성 파이프라인 구현 전까지 각각 `DISCONNECTED`, `STOPPED` 고정. 미구현 기능을 정상으로 표시하지 않는다.
- `ai` — 채팅 감정 분류 파이프라인의 생사. 여론 스냅샷이 최근 10초 안에 갱신됐으면 `RUNNING`. 방송 중에는 채팅이 없어도 Spring이 매초 빈 배치를 중계하므로(13.6) 정상일 때 항상 신선하다.
- `collector` — 치지직 채팅 수집기의 연결 상태(하트비트 기반, 30초 초과 시 `STOPPED` 강등). 수집기는 자동 재연결이 불가능해 재핸드셰이크 루프로 복구하며, 그 사이의 채팅은 영구 유실된다. 수집이 멈춘 것을 스트리머가 알아야 하므로 화면에 노출한다.

`sentiment`는 13.6 채팅 분류 응답의 최근 윈도우 지표를 그대로 중계한 값이며 별도 테이블에 보관하지 않는다. 복구를 위해 최신 1건만 Redis에 스냅샷으로 유지한다.

- **8종을 모두 내려보내고 상위 3종 선택은 프론트가 한다.** 표시 규칙이나 정렬 기준이 바뀌어도 서버 배포가 필요 없고, 8개 실수를 더 보내는 비용이 없다.
- `distribution`의 합은 1.0이다. 프론트가 상위 3종만 그리면 합이 1보다 작아지므로, 나머지를 "기타"로 묶든 상위 3종을 재정규화하든 화면에서 정한다.
- `analyzedChat`이 0이면 `dominant`와 `distribution`은 `null`이다. 채팅이 없거나 분류 호출이 계속 실패한 상태다.
- `engagement`는 시청자 수를 알 수 없는 동안 `null`일 수 있다(예시의 `0.214`처럼 항상 숫자가 아니다). 프론트는 null을 처리해야 한다.

`briefings`와 `repeatedQuestions`는 위험 발언·반복 질문 파이프라인(13.4~13.5) 구현 전까지 **항상 빈 배열**이다. 필드를 미리 두는 이유는 프론트가 스키마를 한 번만 맞추면 되기 때문이다.

`lastEventId`는 해당 세션 Redis Stream의 최신 이벤트 ID다. 아직 생성된 이벤트가 없으면 `"0-0"`을 반환한다. 폴링 체제에서는 소비자가 없지만, WebSocket(10.7)을 되살릴 때 프론트 스키마가 바뀌지 않도록 유지한다.

스냅샷에는 `moderationQueue` 또는 모더레이션 관련 필드를 포함하지 않는다.

### 10.7 WebSocket 연결 (보류 — 2026-07-29)

> **이 절 전체는 보류다.** 실시간 콘솔은 10.6 스냅샷 폴링으로 대체했고 `ws-ticket`을 포함해 이 절의 어떤 것도 구현되어 있지 않다. 보류 근거는 10.6의 개정 주석에 있다. 아래 명세는 삭제하지 않고 남긴다 — 시청자 수가 많은 화면(오버레이 외부 공개 등)이 생겨 푸시가 필요해지면 이 설계에서 다시 시작한다. 단, `SESSION_STATUS_CHANGED`의 Redis Stream(`ws:session:{sessionId}:events`) 발행은 현재도 동작하므로 `lastEventId`와 Stream 보관 규칙은 유효하다.

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
| `SENTIMENT_UPDATED` | 8종 여론 분포 갱신 |
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
      "analyzedChat": 46180,
      "dominant": "JOY",
      "distribution": {
        "JOY": 0.27, "NEUTRAL": 0.25, "SUPPORT": 0.13, "EXCITEMENT": 0.11,
        "CONFUSION": 0.09, "SURPRISE": 0.06, "SADNESS": 0.05, "ANGER": 0.04
      }
    },
    "viewerTimeline": [
      {
        "measuredAt": "2026-07-20T13:05:00Z",
        "viewerCount": 1820
      }
    ],
    "triggers": [
      {
        "triggerId": 8801,
        "triggerType": "CHAT_SURGE",
        "transcriptId": 812,
        "startMs": 4320000,
        "endMs": 4590000,
        "metrics": {
          "peakZ": 5.2,
          "peakEngagement": 0.31,
          "avgViewers": 2480,
          "chatCount": 1840,
          "uniqueChatters": 610,
          "analyzedChat": 1792
        },
        "dominant": "EXCITEMENT",
        "distribution": {
          "EXCITEMENT": 0.41, "JOY": 0.22, "SURPRISE": 0.14, "SUPPORT": 0.10,
          "NEUTRAL": 0.06, "CONFUSION": 0.04, "SADNESS": 0.02, "ANGER": 0.01
        },
        "narrative": "한타 패배 직후 스트리머의 자책 리액션에 채팅이 몰렸습니다.",
        "screenContext": null
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
        "triggerId": 8801,
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
- `PROCESSING` 진행률은 통계 확정 20%, 트리거 분석 50%, 최종 요약 30%로
  계산한다. 트리거가 없는 세션은 트리거 분석 단계를 즉시 충족한다.
- LLM 분석이 실패해도 집계 지표와 트리거 구간은 `PARTIAL` 상태로 반환한다. 트리거는 방송 중에 이미 확정되므로 리포트 생성 실패와 무관하게 남아 있고, `narrative`만 `null`이 된다.
- `metrics.distribution`과 `triggers[].distribution`은 저장된 8종 건수를 `analyzedChat`으로 나눈 값이다. DB에는 비율이 아니라 건수가 들어 있다(13.7).
- **프론트는 건수 상위 3종만 노출한다.** 서버는 8종을 모두 내려보내고 선택은 화면에서 한다. 세션 전체 분포와 트리거 구간 분포에 같은 규칙을 쓴다.
- `screenContext`는 화면 캡처 도입 전까지 항상 `null`이다. 프레임 없이도 전사와 채팅만으로 `narrative`는 생성된다.
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

- 날짜는 UTC `LocalDate`이며 `from`, `to`를 모두 포함한다. 최대 조회 범위는 2년이다.
- 프론트 요청과 호환되도록 임의 날짜 경계를 허용한다. `WEEKLY`는 `from`부터 7일,
  `MONTHLY`는 `from`의 일자를 기준으로 한 달 단위로 나누고 마지막 버킷은 `to`에서 자른다.
- 방송 시간과 평균 시청자는 버킷과 실제로 겹친 시간에 따라 분할한다. 평균 시청자는 겹친 시간을 가중치로 계산한다.
- `totalChat`은 채팅 시각 분포를 `REPORTS`에서 복원할 수 없으므로 방송 시작일 버킷에 귀속한다.
- 방송이 없는 버킷도 반환하며 평균과 합계 지표를 모두 `0`으로 반환한다.
- 직전 값이 없거나 `0`이면 증감률은 `0`으로 반환한다.
- `REPORTS.status`가 `COMPLETED`, `PARTIAL`인 종료 세션만 포함한다.

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

목표 생성:

`POST /growth-goals`

```json
{
  "metricType": "AVG_VIEWERS",
  "targetValue": 3000,
  "unit": "VIEWERS",
  "periodStart": "2026-08-01",
  "periodEnd": "2026-10-31"
}
```

- 생성 상태는 `ACTIVE`다.
- 목표값은 0보다 커야 하며 최대 기간은 2년이다.
- 같은 사용자의 동일 지표 `ACTIVE` 목표는 기간이 겹칠 수 없다.
- 표준 단위는 `AVG_VIEWERS=VIEWERS`, `BROADCASTS_PER_WEEK=COUNT`,
  `TOTAL_CHAT=MESSAGES`, `BROADCAST_MINUTES=MINUTES`다.
- 성공 시 `201 Created`로 생성된 목표와 현재 진행률을 반환한다.

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

반환 필드는 `highlightId`, `triggerId`, `startMs`, `endMs`, `clipUrl`, `thumbnailUrl`이다.
정렬은 `startMs ASC, highlightId ASC`이며 세션당 최대 5개를 생성하므로
페이지네이션하지 않는다. 완료된 리포트에 하이라이트가 없으면 빈 배열을
반환하고, 리포트 생성 중이면 `REPORT_GENERATING(409)`를 반환한다.

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
| 수집기 → Spring | GET | `/internal/v1/sessions/{sessionId}/collector-credentials` | 치지직 핸드셰이크용 Access Token·채널 ID 발급 |
| 수집기 → Spring | POST | `/internal/v1/sessions/{sessionId}/collector-status` | 수집기 연결 상태 보고(하트비트 겸용) |
| 수집기 → Spring | POST | `/internal/v1/sessions/{sessionId}/collection-gaps` | 수집 공백 구간 보고 |
| Spring → 수집기 | POST | `/collectors/{sessionId}` | 세션 수집 시작 |
| Spring → 수집기 | DELETE | `/collectors/{sessionId}` | 세션 수집 종료와 잔여 Flush |
| 음성 중계 → Spring | POST | `/internal/v1/sessions/{sessionId}/audio-chunks` | 음성 청크 저장·STT 작업 등록 |
| FastAPI → Spring | POST | `/internal/v1/sessions/{sessionId}/transcripts:batch` | STT 결과 배치 저장 |
| Spring → FastAPI | POST | `/internal/v1/ai/stt` | 음성 청크 STT 변환 |
| Spring → FastAPI | POST | `/internal/v1/ai/risk-speech` | 위험 발언 분류와 조언 생성 |
| Spring → 채팅 AI | POST | `/broadcasts/{sessionId}/ingest` | 1초 배치 채팅 분류와 실시간 윈도우 지표 |
| Spring → 채팅 AI | POST | `/broadcasts/{sessionId}/summary` | LLM 여론 요약(보류 — 채팅 AI 미구현) |
| Spring → 채팅 AI | DELETE | `/broadcasts/{sessionId}` | 방송 종료 시 윈도우·요약 캐시 정리 |
| Spring → FastAPI | POST | `/internal/v1/ai/repeated-questions` | 반복 질문 추출 |
| Spring → FastAPI | POST | `/internal/v1/ai/promise-detection` | STT에서 약속 후보 추출 |
| Spring → FastAPI | POST | `/internal/v1/ai/growth-actions` | 성장 목표의 다음 액션 제안 |

세션 리포트 생성은 내부 API가 아니다. Spring이 자기 DB의 트리거·전사·채팅을 읽어 LLM을 직접 호출한다(13.7). 따라서 `session-reports` 요청, `highlight-detection` 요청, `analysis-results` Callback 세 Endpoint는 두지 않는다.

채팅 AI는 별도 서버(기본 포트 8001)이며 요청·응답 규격의 원본은 `AI/CHAT_AI_API.md`다. 이 문서에는 Spring이 저장·중계하는 부분만 적는다.

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

각 항목은 `VIEWER_METRICS(session_id, measured_at, viewer_count)`에 저장한다. `(session_id, measured_at)` Unique가 재시도 중복을 흡수하며, `duplicateCount`는 이미 저장돼 있어 건너뛴 건수다.

폴링에 실패한 시점은 아무것도 보내지 않는다. 마지막 정상값을 새 측정치로 다시 써서 보내면 진짜와 구분되지 않는 합성 행이 쌓여 리포트의 `viewerTimeline`과 평균·최고 시청자가 왜곡된다. `HIGHLIGHT_SPEC.md` 가드 4의 "마지막 정상값 최대 5분"은 저장 계층이 아니라 판정 계층의 규칙이다.

#### 13.3.2-1 수집기 자격 정보

`GET /internal/v1/sessions/{sessionId}/collector-credentials`

```json
{
  "success": true,
  "data": {
    "accessToken": "chzzk-access-token",
    "channelId": "abc123"
  }
}
```

플랫폼 토큰은 암호화되어 저장되고 복호화 키는 Spring만 갖는다(NFR-S-03). 수집기가 DB와 키를 직접 보지 않게 하는 경로다. 만료가 임박했으면 Spring이 갱신해서 돌려준다.

수집기는 시작 시점에 한 번 받아 들고 있는 것이 아니라 **핸드셰이크마다 새로 받는다.** 들고 있던 토큰이 만료되면 이후 재핸드셰이크가 전부 401로 실패해 남은 방송 내내 수집이 죽는다. 조회 요청이라 `X-Request-Id` 대상이 아니다.

#### 13.3.2-2 수집기 상태 보고

`POST /internal/v1/sessions/{sessionId}/collector-status`

```json
{
  "status": "RUNNING",
  "occurredAt": "2026-07-20T14:31:00Z"
}
```

`status`는 `RUNNING`, `RECONNECTING`, `STOPPED`다. 10.6의 `services.collector`가 이 값을 읽는다. 하트비트를 겸하므로 상태가 바뀌지 않아도 주기적으로 온다. 보고가 `laive.collector.heartbeat-timeout`(기본 30초)을 넘게 끊기면 Spring이 `STOPPED`로 내린다 — 수집기 프로세스가 죽으면 마지막 상태를 갱신할 주체가 사라져, 저장값을 그대로 믿으면 화면이 영원히 "정상 수집 중"을 보여준다.

최신값 하나만 남기는 덮어쓰기라 재시도가 같은 결과로 수렴하므로 `X-Request-Id`를 받지 않는다.

#### 13.3.2-3 수집 공백 구간 보고

`POST /internal/v1/sessions/{sessionId}/collection-gaps`

```json
{
  "startedAt": "2026-07-20T14:31:00Z",
  "endedAt": null,
  "reason": "DISCONNECTED"
}
```

`COLLECTION_GAPS(session_id, started_at, ended_at, reason)`에 저장한다(V9). `reason`은 `DISCONNECTED`, `AUTH_FAILED`, `SUBSCRIBE_FAILED`, `COLLECTOR_STOPPED`다.

끊긴 시점에 `endedAt` 없이 먼저 열고, 재핸드셰이크로 복구한 뒤 같은 `startedAt`으로 다시 보내 닫는다. 처음부터 닫힌 구간으로 받지 않는 이유는 수집기가 공백 도중에 죽으면 그 공백이 영원히 보고되지 않기 때문이다. 끝내 닫히지 않은 구간은 세션 종료 시 Spring이 종료 시각으로 닫는다.

`(session_id, started_at)` Upsert라 같은 보고를 몇 번 보내도 행이 늘지 않으므로 `X-Request-Id`를 받지 않는다.

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

채팅 감정 분류와 실시간 지표:

`POST /broadcasts/{sessionId}/ingest`

**1초마다 호출하며 그 1초 동안 수신한 채팅을 전부 싣는다.** 채팅이 없어도 빈 배열로 호출해야 윈도우가 시간에 따라 미끄러지고 시청자 수가 갱신된다.

```json
{
  "viewers": 2417,
  "chats": [
    { "uid": "a1b2c3...", "msg": "신규 챔피언 바로 구매 가자", "ts": 1753690000123 }
  ]
}
```

```json
{
  "results": [
    { "label": "응원/호감", "score": 0.91 }
  ],
  "window": {
    "windowSec": 180,
    "chatCount": 6185,
    "uniqueChatters": 1168,
    "viewers": 2417,
    "engagement": 0.214,
    "distribution": { "웃음/재미": 0.27, "중립": 0.25, "…": 0.0 },
    "dominant": "웃음/재미"
  }
}
```

Spring의 처리는 두 갈래다.

- `results`는 요청 `chats`와 **같은 순서**의 건별 라벨이다. 라벨 코드로 변환해 `CHAT_MESSAGES.sentiment_label`에 원본과 함께 저장한다.
- `window`는 저장하지 않고 10.6의 `sentiment`로 그대로 중계한다. 재연결 복구용으로 최신 1건만 Redis에 남긴다.

**호출이 실패하면 재시도하지 않고 그 배치는 건너뛴다.** 채팅 원본은 라벨 없이 저장하고 `sentiment_label`은 `NULL`로 둔다. AI 서버 장애가 채팅 수집을 멈추게 해서는 안 되며, 라벨은 저장된 원본으로 나중에 재분류할 수 있지만 원본 유실은 복구할 수 없다. 타임아웃은 2초를 권장한다.

라벨 코드 표. AI 서버는 한국어 문자열을 주고 DB와 API는 ASCII 코드를 쓴다. 변환은 이 응답을 저장하는 지점 한 곳에서만 한다.

| AI 서버 라벨 | 저장·API 코드 |
| --- | --- |
| `웃음/재미` | `JOY` |
| `환호/감탄` | `EXCITEMENT` |
| `응원/호감` | `SUPPORT` |
| `놀람` | `SURPRISE` |
| `슬픔/아쉬움` | `SADNESS` |
| `분노/불만` | `ANGER` |
| `혼란/질문` | `CONFUSION` |
| `중립` | `NEUTRAL` |

라벨 집합은 채팅 분류 모델 버전에 묶여 있다. 모델을 교체해 라벨이 바뀌면 이 표와 `CHAT_MESSAGES`·`REPORTS`·`TRIGGERS`의 컬럼을 함께 마이그레이션해야 한다.

LLM 여론 요약 (보류 — 2026-07-29):

`POST /broadcasts/{sessionId}/summary`

> **보류.** chat-ai 서버에 `/summary` Endpoint가 아직 구현되어 있지 않아 Spring 쪽 스케줄러·저장 경로도 만들지 않았다. AI 서버 구현이 끝나면 아래 규칙대로 잇는다.

3분 주기 스케줄러는 `{"mode":"AUTO"}`, 프론트 버튼은 `{"mode":"MANUAL"}`로 호출한다. Spring은 성공 응답의 `summary`로 `LIVE_BRIEFINGS(CHAT_SUMMARY)`를 생성한다. 윈도우 채팅 부족(`422`)과 쿨다운·중복 호출(`409`)은 정상 거절이므로 오류로 기록하지 않는다.

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

세션 리포트는 **Spring 내부에서 생성한다.** 전용 AI Endpoint도 Callback도 두지 않고, 대용량 입력을 위한 S3 집계 파일도 만들지 않는다.

프로세스 경계를 없앤 이유는 세 가지다.

- 방송 종료 이벤트를 실어 나르는 Transactional Outbox가 이미 있다. 선점 임대와 재시도 간격, 최대 시도 후 `DEAD` 전이를 갖춘 잡 큐라, 별도 워커 큐를 붙이면 재시도와 멱등 로직이 두 군데로 갈라진다.
- 리포트 입력이 전부 Spring의 DB에 있다. 프로세스를 건너려면 세션 전체 전사·채팅을 직렬화해 스토리지에 올리고 반대편에서 다시 읽어야 하는데, 같은 프로세스 안에서는 조회 쿼리 하나다.
- 화면 캡처를 후순위로 미루면서 리포트 생성이 텍스트 입력·텍스트 출력이 되었다. Python이어야 했던 이유인 Vision이 빠졌다.

LLM은 OpenAI 호환 Endpoint로 호출한다. 프롬프트는 Java 문자열이 아니라 리소스 파일로 분리해 AI 담당자가 텍스트만 고칠 수 있게 한다.

#### 트리거 앵커

리포트의 기준점은 방송 중 확정된 트리거다. 하이라이트 판정은 Spring이 자체 수행하며(알고리즘 명세는 `AI/chat-ai/HIGHLIGHT_SPEC.md`), 구간이 확정될 때마다 `TRIGGERS`에 한 행을 남긴다.

> **구현 현황 (2026-07-29).** 판정과 `TRIGGERS` 기록까지 구현됐다(`broadcast/realtime` 패키지). 확정 시 구간 내 통계를 단일 SQL로 집계해 저장하므로 8종 감정 건수 합 == `analyzed_chat` CHECK가 구조적으로 성립한다. `narrative`·`REPORT_INSIGHTS` 생성과 `TRIGGER_ANALYSIS_REQUESTED` Outbox 발행은 리포트 생성 단계(이 절의 "생성 흐름")에서 후속 구현한다. 판정 파라미터는 백테스트 미검증 초기값으로 `laive.highlight.*` 설정에 있다.

| 컬럼 | 내용 |
| --- | --- |
| `session_id` | 소유 세션. `BROADCAST_SESSIONS` 참조 |
| `trigger_type` | 발생 사유. 현재 채팅 급증(`CHAT_SURGE`) 하나다 |
| `start_ms`, `end_ms` | 세션 시작 기준 오프셋 |
| `started_at`, `ended_at` | 같은 구간의 절대 시각 |
| `transcript_id` | 구간 시작에 겹치는 STT 청크 |
| `peak_z`, `peak_engagement` | 판정 지표의 구간 최댓값 |
| `avg_viewers`, `chat_count`, `unique_chatters` | 구간 규모 |
| 8종 감정 건수, `analyzed_chat` | 구간 채팅의 라벨별 건수 |
| `narrative` | 급증 원인 설명. LLM이 채운다 |
| `screen_context` | 화면 맥락. 캡처 도입 전까지 `NULL` |

`TRIGGERS`는 ERD의 `REPORT_SEGMENTS`를 대체한다. 트리거는 방송 **중** 확정되는데 `REPORTS` 행은 방송 종료 시 생성되므로, `report_id`를 필수로 요구하는 구조에는 저장할 수 없다. 방송 시작 시점에 리포트를 미리 만들어 우회하면 대시보드의 `reportStatus`가 방송 내내 `PROCESSING`으로 표시된다. 세션에 매다는 편이 자연스럽고, 리포트 생성이 실패해도 앵커가 남아 재시도할 수 있다.

시각은 오프셋과 절대 시각을 **둘 다** 저장한다. 소비자마다 필요한 형태가 다르기 때문이다 — `TRANSCRIPTS`는 오프셋으로, `CHAT_MESSAGES`는 절대 시각으로 조회한다. 확정 시점의 Spring은 둘 다 알고 있고 행 수가 세션당 수십 개라, 조회할 때마다 세션 시작 시각과 조인해 변환하는 것보다 안전하다.

보관 정책은 걸지 않는다. 트리거는 원본이 아니라 구간 요약이고, 채팅 원본이 보관 기간으로 삭제된 뒤에 남겨야 할 것이 바로 그 요약이다. `VIEWER_METRICS`에 적용한 것과 같은 판단이다.

트리거가 확정되지 않은 세션도 리포트를 만든다. 이때 `triggers`는 빈 배열이고 요약과 인사이트는 세션 전체 지표만으로 생성한다.

#### 생성 흐름

1. 세션 종료 시 통계를 집계해 `REPORTS`에 커밋한다. LLM을 기다리지 않는다.
2. Outbox가 트리거마다 분석 작업을 발행한다. 한 이벤트에서 트리거 전부를 순차 처리하면 LLM 지연이 쌓여 선점 임대 시간을 넘기고 다른 워커가 같은 작업을 중복 선점한다. 트리거 하나가 실패했을 때 그것만 재시도하기 위해서이기도 하다.
3. 워커가 트리거별로 LLM을 호출해 `TRIGGERS.narrative`를 채운다. 재생성 시 새 행을 만들지 않고 갱신한다.
4. 모든 트리거 분석이 끝나면 세션 요약과 `REPORT_INSIGHTS`를 생성하고 `COMPLETED`로 전이한다.

LLM 호출은 DB 트랜잭션 밖에서, 앱 공용 스케줄러와 분리한 실행기에서 수행한다. 그러지 않으면 LLM 지연이 다른 스케줄 작업을 밀어낸다.

#### 트리거별 LLM 입력

트리거 한 건마다 아래를 모아 한 번 호출한다.

- 방송 메타데이터 — 제목, 카테고리, 언어, 총 길이
- 트리거 지표 — 종류, 구간, `peak_z`, `peak_engagement`, `chat_count`, `unique_chatters`, 8종 감정 분포
- 구간 전사 — `TRANSCRIPTS`에서 `start_ms`~`end_ms`와 겹치는 청크
- 구간 채팅 — `CHAT_MESSAGES`에서 `started_at`~`ended_at` 사이, 라벨 포함

지표를 함께 넘기는 것이 핵심이다. "이 구간에서 z가 5.2까지 튀었다"를 아는 상태로 전사와 채팅을 읽어야 급증의 원인을 지목할 수 있다. 지표 없이 넘기면 구간 요약에 그친다.

채팅은 구간 원문을 그대로 넘긴다. 트리거 구간은 정의상 채팅이 몰린 곳이라 양이 많으므로, 프롬프트 한도를 넘기면 반복 표현을 건수로 접거나 표본을 뽑는 압축을 호출 직전 단계에서 수행한다.

#### 통계 컬럼

| `REPORTS` 컬럼 | 집계 원천 |
| --- | --- |
| `avg_viewers`, `peak_viewers` | `VIEWER_METRICS`의 세션 구간 평균·최댓값 |
| `total_chat` | `CHAT_MESSAGES`의 세션 건수 |
| `analyzed_chat` | `CHAT_MESSAGES` 중 `sentiment_label`이 있는 건수 |
| `joy_count` … `neutral_count` | `CHAT_MESSAGES`를 `sentiment_label`로 묶은 건수 |

- 8종 건수의 합은 `analyzed_chat`과 같다. **비율은 저장하지 않고 조회 시점에 나눈다.** 비율을 반올림해 저장하면 8개의 합이 1에서 벌어지고, 여러 세션을 묶는 성장 리포트에서 방송 규모와 무관하게 세션당 같은 비중이 되어 버린다.
- `analyzed_chat / total_chat`이 분류 커버리지다. 방송 중 분류 호출이 자주 실패하면 이 값이 낮아지므로 `PARTIAL` 판정 근거로 쓴다.
- `CHAT_MESSAGES`는 보관 기간이 지나면 삭제되지만 `REPORTS`는 영속이다. 감정 건수는 리포트 생성 시점에 확정 저장하며 이후 재계산하지 않는다.
- 통계 집계는 LLM 분석을 기다리지 않는다. 세션 종료 시 먼저 커밋하고, LLM 분석이 최종 실패하면 `PARTIAL`로 집계 지표와 트리거 구간만 제공한다.

#### 하이라이트

트리거가 곧 하이라이트 후보이므로 별도 탐지 요청을 두지 않는다. 클립으로 만들 트리거를 골라 `HIGHLIGHTS(report_id, trigger_id, start_ms, end_ms)`에 저장하고, 클립 생성 후 `clip_url`과 `thumbnail_url`을 갱신한다. 모든 트리거가 클립이 되지는 않으므로 두 테이블은 분리해 둔다.

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
| 채팅 건별 감정 라벨 | `CHAT_MESSAGES.sentiment_label` |
| 실시간 여론 분포 | 저장하지 않고 중계. 재연결 복구용 최신 1건만 Redis |
| 하이라이트 트리거와 구간 감정 | `TRIGGERS` |
| 반복 질문 | `REPEATED_QUESTIONS` |
| 약속 후보 | `PROMISES`, `LIVE_BRIEFINGS` |
| 세션 요약·통계·개선안 | `REPORTS`, `REPORT_INSIGHTS` |
| 트리거 원인 설명 | `TRIGGERS.narrative`, `screen_context` |
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
| `REPORT_NOT_FOUND` | 404 | 종료 전이거나 리포트가 존재하지 않음 |
| `REPORT_GENERATING` | 409 | 생성 중인 리포트의 완료 결과 조회 |
| `GROWTH_GOAL_NOT_FOUND` | 404 | 성장 목표 없음 |
| `INVALID_GROWTH_GOAL` | 400 | 성장 목표값·기간·단위 조합 오류 |
| `GROWTH_GOAL_STATE_CONFLICT` | 409 | 성장 목표 상태 전이 또는 활성 기간 중복 |
| `INVALID_GROWTH_PERIOD` | 400 | 성장 리포트 기간 역전·경계 불일치·최대 범위 초과 |
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
- [x]  `GROWTH_GOALS.metric_type/status` Enum과 단위 문자열 정책 확정
- [ ]  소셜 제공자 이메일과 기존 이메일 계정의 안전한 연결 정책
- [ ]  비밀번호 재설정 메일 발송 서비스와 Redis TTL 설정
- [ ]  Electron 앱의 음성 수집 방식과 권한
- [ ]  Spring·FastAPI 서비스 토큰 회전, 네트워크 접근 제한 또는 mTLS 적용
- [x]  `X-Request-Id` 멱등 처리 Redis TTL과 동일 요청 판별 범위 확정. TTL 10분, 키는 `internal:request-id:{id}`, 판별 범위는 요청 하나(배치 하나)다. 완료된 요청은 응답 본문까지 보관해 재시도에 같은 응답을 돌려주고, 처리 중인 요청과 겹치면 409를 준다. 처리에 실패하면 표식을 지운다 — 남겨두면 그 ID가 TTL이 끝날 때까지 409로 막혀 일시적 실패가 영구 실패가 된다
- [ ]  채팅·시청자 지표·STT 배치의 최대 항목 수와 요청 크기 제한. 채팅·시청자 지표는 `laive.internal.max-batch-items`(기본 1000)로 걸었고 초과 시 413이다. STT 배치와 바이트 단위 크기 제한은 아직 없다
- [ ]  실시간 STT·위험 발언·채팅 분석의 Timeout, 재시도, Circuit Breaker 정책
- [ ]  음성 청크의 S3 보관 기간과 삭제 작업 (리포트 입력 파일은 13.7에서 폐지)
- [x]  스키마 변경 마이그레이션: `CHAT_MESSAGES.sentiment_label` 추가, `TRIGGERS` 신설, `REPORTS`의 3분류 비율 컬럼을 8종 건수와 `analyzed_chat`으로 교체, `REPORT_SEGMENTS`·`SENTIMENT_SNAPSHOTS` 폐기, `HIGHLIGHTS.report_segment_id` → `trigger_id` (V8)
- [ ]  **트리거 판정 구현 시 함께 처리할 것**
    - `chat_message_counts_1m` 연속 집계 폐기. V3가 적은 세 용도(하이라이트 z-score 입력, `reports.total_chat` 집계, 성장 리포트 누적 채팅)가 모두 다른 경로로 바뀌어 소비처가 없다. 판정은 Spring이 인메모리 60초 윈도우로 수행하고, 세션 총량은 `REPORTS.total_chat`, 구간 채팅량은 `TRIGGERS.chat_count`에 영구 저장된다. 방송 중 채팅량 그래프를 리포트에 넣기로 하면 이 결정을 뒤집고 유지해야 한다 — 원본 90일 보존을 넘긴 이력은 재생성할 수 없다.
    - `BroadcastOutboxEventType` 자바 Enum에 `TRIGGER_ANALYSIS_REQUESTED`, `REPORT_FINALIZATION_REQUESTED` 추가. V8이 DB CHECK는 먼저 열어뒀고, 핸들러 없이 Enum에 값을 넣으면 디스패처가 처리기를 찾지 못하는 값이 생기므로 핸들러와 함께 추가한다.
    - `BroadcastSessionFinalizationService`가 통계 확정 후 `PARTIAL`로 끝내는 자리에 트리거별 분석 작업 발행을 넣는다.
    - **수집 공백 가드.** `HIGHLIGHT_SPEC.md`에 가드 9로 추가했다. 치지직 socket.io는 자동 재연결이 되지 않아 재핸드셰이크 동안 채팅이 유실되는데, 그 구간에 `uniq`가 0으로 떨어지면 15분 baseline에 인위적으로 낮은 값이 채워지고 **복구 직후의 정상 채팅이 급증으로 오탐**된다. median·MAD가 극단값에 강건하다는 전제는 공백이 없다는 가정에 기대고 있다. 공백 구간의 틱은 판정에서 제외하고 baseline에도 넣지 않는다.
    - ~~위 가드의 입력이 되는 **수집 공백 구간 저장 위치**를 이 마이그레이션에서 함께 만든다.~~ **완료 — V9 `COLLECTION_GAPS`.** 트리거 마이그레이션을 기다리지 않고 먼저 만들었다. 수집기가 지금 공백을 만들고 있어 저장할 곳이 그때까지 없으면 그 사이의 유실 구간이 영영 기록되지 않기 때문이다. 판정이 붙을 때 이 테이블을 읽어 해당 구간의 틱을 제외하고 baseline에도 넣지 않으면 된다.
- [ ]  Outbox 선점 임대 시간 재산정 (LLM 지연 반영)
- [x]  **치지직 채팅 수집기.** `AI/chat-collector/`에 FastAPI 제어 서버와 세션별 전용 스레드로 구현했다. Spring의 `ChatCollectorGateway`가 `POST/DELETE /collectors/{sessionId}`로 제어한다. 확정된 것:
    - 자동 재연결을 끄고(`reconnection=False`) 재핸드셰이크 루프를 직접 돈다. 세션 인증 URL의 쿼리스트링 인증값이 재사용되지 않아 같은 URL로 재연결하면 `auth fail`로 끊긴다. 전체 핸드셰이크(자격 조회 → auth → connect → `SYSTEM` sessionKey → subscribe)를 다시 밟는다. 백오프는 1초에서 시작해 최대 30초.
    - 자격 정보는 핸드셰이크마다 새로 받는다(13.3.2-1). 들고 있던 Access Token이 만료되면 이후 재핸드셰이크가 전부 401로 실패해 남은 방송 내내 수집이 죽는다.
    - `sent_at`은 `messageTime`(epoch ms)을 쓴다. `eventSentAt`은 타임존 없는 KST 로컬 문자열이라 UTC로 파싱하면 9시간 틀어진다. 다만 `messageTime`은 클라이언트 시계라(가드 8) 서버 수신 시각과 5분 넘게 벌어지면 수신 시각으로 대체한다 — 어긋난 시계 하나가 `chat_messages`의 먼 과거·미래 청크에 행을 만들고 리플레이 정렬을 깬다.
    - 시청자 수 폴링은 10초 주기. **실패한 틱은 아무것도 보내지 않는다** — 가드 4의 "마지막 정상값 최대 5분"은 저장이 아니라 판정 계층의 규칙이라, 수집기는 마지막 정상값과 그 나이(`viewerStale`)를 상태로 노출해 판정이 같은 기준을 쓰게 한다.
    - `senderChannelId`는 `sha256:{hex}`로 해시해 `viewerHash`로만 보낸다. 소금(`COLLECTOR_VIEWER_HASH_SALT`)을 운영 전에 정하고 이후 바꾸지 않는다 — 바꾸면 같은 시청자가 다른 해시가 되어 `VIEWER_PROFILES`에 중복 인격이 생긴다.
- [ ]  `profile.userRoleCode`와 `emojis`를 저장할 자리. 수집기가 읽고는 있으나 13.3.1 요청과 `CHAT_MESSAGES` 모두 컬럼이 없어 전달하지 못한다. 스트리머 본인 채팅을 여론 집계에서 제외하려면, 이모티콘 밀도를 쓰려면 요청 스키마와 테이블을 함께 늘려야 한다
- [x]  수집 엔드포인트 2개 구현 (`chat-messages:batch`, `viewer-metrics:batch`). 서비스 토큰 필터 체인(`/internal/**`)과 `X-Request-Id` 멱등 가드를 함께 넣었다. 수집기 자격 발급·상태 보고·공백 보고 Endpoint 3개가 13.3에 추가됐다
- [ ]  Spring의 LLM 클라이언트 선택 — Spring AI의 Spring Boot 4 지원 버전 확인, 미지원 시 GMS OpenAI 호환 Endpoint 직접 호출
- [ ]  트리거 구간 채팅 원문이 프롬프트 한도를 넘길 때의 압축 규칙 (반복 표현 접기, 표본 추출) 확정
- [ ]  `TRIGGERS.trigger_type` Enum 확정. 현재 채팅 급증 하나이며 시청자 급변 트리거는 판정 로직이 없다
- [ ]  8종 라벨 집합이 채팅 분류 모델 버전에 묶여 있으므로 모델 교체 시 컬럼 마이그레이션 절차 합의
- [ ]  화면 캡처 도입 시점과 방식. 트리거는 사후 판정이라 발생 시점에 캡처할 수 없으므로 상시 링버퍼 캡처 또는 VOD 사후 추출 중 선택
- [ ]  `AI/BACKEND_API.md`의 STT·위험 발언·약속 전송 방향과 13.4·13.5의 불일치 정리 (AI가 Spring을 호출하는 실제 구현과 반대로 적혀 있다)
- [ ]  성장 액션 생성(`/internal/v1/ai/growth-actions`)도 Spring 내부 LLM 호출로 옮길지 결정. 현재 리포트만 옮겨 두 방식이 공존한다
- [ ]  목업에 표시된 하이라이트 점수·탐지 사유가 필요하면 `HIGHLIGHTS` 컬럼 추가
- [ ]  실제 채팅 닉네임이 필요하면 `VIEWER_PROFILES`에 공개용 표시명 저장 정책과 컬럼 추가 검토
- [ ]  목업의 후원 채팅 리플레이가 필요하면 후원 이벤트 테이블 추가 검토
- [ ]  포트폴리오의 구독자 수를 표시하려면 플랫폼 조회 방식 또는 저장 컬럼 추가
- [ ]  포트폴리오 연락처 공개 동의 정책
- [ ]  음성, 채팅, STT 전문의 보관 기간
- [ ]  Redis Streams의 세션별 최대 길이, 방송 중 TTL 갱신, 종료 후 30분 보관 정책 부하 테스트

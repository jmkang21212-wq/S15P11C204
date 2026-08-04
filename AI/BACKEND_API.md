# AI 서버 ↔ Spring 연동 규격

> 대상: **백엔드 · 프론트엔드**
> 프론트 ↔ AI WebSocket 규격은 [FRONTEND_API.md](FRONTEND_API.md)에 따로 있다.

AI 서버가 방송 중 실시간으로 세 가지를 Spring에 보내 저장한다.

| 무엇 | 언제 | 방식 | 저장 테이블 |
|---|---|---|---|
| **전사** (모든 발화) | 2초마다 또는 10건 모이면 | 배치 POST | `TRANSCRIPTS` |
| **위험 경고** | 감지 즉시 | 개별 POST | `RISK_EVENTS` + `LIVE_BRIEFINGS` |
| **약속** | 감지 즉시 | 개별 POST | `PROMISES` + `LIVE_BRIEFINGS` |

```
프론트 ──오디오(WS)──> AI 서버 ──POST──> Spring ──> DB
      <──경고(WS)────
```

프론트는 AI 서버와 WebSocket으로 경고만 주고받는다. **저장은 전적으로 AI → Spring 경로**로 이뤄지며, 프론트 화면 동작과 무관하다.

**구현 상태: AI · Spring · 프론트 연동 완료** (2026-07-31 — 아래 "프론트 연동" 참고)

---

## 인증

```
Authorization: Bearer <SPRING_TOKEN>
Content-Type: application/json
```

Spring의 `laive.internal.service-token`과 같은 값이어야 한다. `/internal/**`는 사용자 JWT가 아니라 이 서비스 토큰으로 인증한다(`InternalSecurityConfig`).

| 환경변수 | 위치 |
|---|---|
| `SPRING_TOKEN` | `AI/.env` |
| `INTERNAL_SERVICE_TOKEN` | `BackEnd/spring/laive/.env` |

---

## sessionId는 숫자다

경로의 `{sessionId}`는 **`BROADCAST_SESSIONS.id`(숫자)**다. 프론트가 WebSocket 핸드셰이크에 쓰는 `session_id`(`"user101-a3f9c2d1"` 같은 문자열)와 **다른 값**이다.

| | 값 | 용도 |
|---|---|---|
| `session_id` | `"user101-a3f9c2d1"` | 프론트가 생성. WS 탭 충돌 방지, AI 내부 라우팅 |
| `broadcastSessionId` | `9002` | Spring DB의 방송 세션. 저장에 필수 |

AI 서버는 두 값의 대응을 핸드셰이크에서 받아 기억한다. 번호가 없으면 감지와 프론트 전송은 그대로 돌고 **Spring 저장만 건너뛴다.**

---

## 1. 전사 저장

```
POST /internal/v1/sessions/{sessionId}/transcripts:batch
```

```json
{
  "items": [
    { "text": "안녕하세요 오늘 방송 시작합니다",
      "startedAt": "2026-07-20T13:01:48.000Z",
      "endedAt":   "2026-07-20T13:01:50.000Z" },
    { "text": "오늘은 발로란트 할 거예요",
      "startedAt": "2026-07-20T13:01:58.000Z",
      "endedAt":   "2026-07-20T13:02:00.000Z" }
  ]
}
```

```json
{ "success": true, "data": { "acceptedCount": 2, "duplicateCount": 0 } }
```

---

## 2. 위험 경고 저장

```
POST /internal/v1/sessions/{sessionId}/risk-events
```

```json
{
  "source": "DICTIONARY",
  "severity": "HIGH",
  "category": "PROFANITY",
  "text": "아 씨발 뭐야 이게",
  "startedAt": "2026-07-20T13:02:08.000Z",
  "endedAt":   "2026-07-20T13:02:10.000Z",
  "reason": null
}
```

| 필드 | 값 |
|---|---|
| `source` | `DICTIONARY` \| `LLM` |
| `severity` | `HIGH` \| `MEDIUM` \| `LOW` |
| `category` | `PROFANITY` `POLITICS` `DISCRIMINATION` `SEXUAL` `DEFAMATION` `OTHER` |
| `reason` | LLM 판정 근거. 사전 매칭이면 `null` |

`RISK_EVENTS` 저장과 함께 `LIVE_BRIEFINGS(RISK)`가 생성된다. 브리핑 문구는 `reason`이 있으면 그것을, 없으면 기본 문구를 쓴다.

> AI 내부 카테고리(`insult`, `threat`, `aggressive`, `harassment`)는 `sink/spring.py`의 `_RISK_CATEGORY`가 위 6종으로 변환해서 보낸다. DB의 `ck_risk_events_risk_category`와 맞추기 위해서다.

응답은 `201`과 저장된 행 ID다.

```json
{ "success": true, "data": { "id": 4821 } }
```

---

## 3. 약속 저장

```
POST /internal/v1/sessions/{sessionId}/promises
```

```json
{
  "content": "공포게임 방송",
  "rawText": "2주 안으로 공포게임 할게요",
  "deadlineAt": "2026-08-03T12:00:00.000Z",
  "spokenAt":   "2026-07-20T13:02:18.000Z"
}
```

`PROMISES`에 `DETECTED` 상태로 저장되고 `LIVE_BRIEFINGS(PROMISE)`가 생성된다.

**기한은 AI가 이미 계산해서 보낸다.** `"2주 안으로"` 같은 한국어 표현을 Spring이 파싱할 일은 없다.

**AI는 기한이 있는 약속만 보낸다.** 날짜를 계산할 수 없는 약속(`"다음에 꼭 할게요"`)과 날짜 없는 조건부 약속(`"구독자 1000명 되면"`)은 감지 단계에서 버린다. 캘린더 등록과 이행 판정이 약속 기능의 목적이라 날짜가 없으면 쓸 수 없기 때문이다. 따라서 이 엔드포인트로 오는 `deadlineAt`은 **항상 값이 있다.**

Spring은 이 값을 `due_date`(DATE)로 변환해 저장한다. 스키마상 `due_date`는 여전히 NULL을 허용하는데, 스트리머가 확정(10.8) 단계에서 날짜를 비우거나 직접 만든 약속이 있을 수 있어서다(명세 1.5). 이 경로로만 들어오는 값이 아니라는 뜻이다.

같은 약속을 반복해서 말해도 15분 안에는 한 번만 온다. 중복 제거는 AI가 한다.

---

## 구현 시 알아둘 것

### 1. 경고가 전사보다 먼저 도착한다

전사는 최대 2초 모았다가 보내고, 경고는 즉시 보낸다. 순서가 뒤집힌다.

```
t=0.4s  경고 도착
t=2.0s  전사 도착
```

`RISK_EVENTS.transcript_id`는 NOT NULL이라 그냥 두면 저장이 실패한다.

**해결 방식** — 경고와 약속 payload에 발화 원문(`text` / `rawText`)이 함께 온다. Spring은 그 원문으로 전사를 **Upsert**한 뒤 연결한다. 나중에 배치로 같은 발화가 와도 `uq_transcripts_session_range (session_id, start_ms, end_ms)`가 흡수한다. **도착 순서를 따질 필요가 없다.**

### 2. 시각은 Spring이 환산한다

AI는 벽시계 시각(ISO 8601 UTC)을 보내고, DB는 방송 시작 기준 밀리초로 저장한다(명세 4.2). AI는 방송 시작 시각을 모르므로 Spring이 세션의 `started_at`으로 계산한다.

방송 시작보다 이른 발화는 `ck_transcripts_start_ms`(0 이상)에 걸리므로 0으로 당긴다.

### 3. 같은 데이터가 두 번 올 수 있다

네트워크 오류나 타임아웃 시 AI가 재시도한다. 전사는 Unique Upsert라 안전하고, 위험 경고·약속은 중복 저장될 수 있다. 실시간 경고 특성상 스트리머가 같은 경고를 두 번 보는 정도라 멱등 가드를 걸지 않았다.

### 4. 응답 코드에 따라 AI 동작이 다르다

| 응답 | AI 서버 동작 |
|---|---|
| `2xx` | 성공 처리 |
| `4xx` | **재시도하지 않고 버린다.** 요청 자체가 잘못됐다고 보기 때문 |
| `5xx` / 타임아웃 | 0.5 → 1 → 2초 간격으로 최대 3회 재시도 |

**저장에 일시적으로 실패했다면 반드시 5xx를 반환해야 한다.** 4xx를 주면 그 데이터는 영영 사라진다.

---

## 프론트 연동 (완료 — 2026-07-31)

세 가지가 모두 반영되어 DB 저장이 동작한다.

| # | 반영 내용 | 위치 |
|---|---|---|
| 1 | `POST /broadcast-sessions` 실연동 (`USE_MOCK = false`) — 진짜 세션 번호 확보 | `api/broadcastSessions.ts` |
| 2 | 세션 번호를 **숫자 그대로** `useAudioStream` → `AudioStreamClient`에 전달 | `BroadcastConsolePage.tsx` |
| 3 | 핸드셰이크에 `broadcastSessionId` 포함 | `AudioStreamClient.ts` `connect()` |

`session_id`의 UUID 접미사 규칙은 그대로다 — 접속마다 `AudioStreamClient`가 새로 붙인다(탭 충돌 방지, AI 내부 라우팅).

**동작 확인** — AI 로그에 아래 경고가 나오면 `broadcastSessionId` 전달이 빠진 것이다(감지·프론트 표시는 되지만 Spring 저장만 건너뛴다).

```
broadcastSessionId가 없어 Spring 저장을 건너뜁니다: session_id=...
```

---

## AI 서버 설정

`.env`에 아래를 채우면 전송이 켜진다.

```
SPRING_BASE_URL=http://localhost:8080
SPRING_TOKEN=<Spring의 INTERNAL_SERVICE_TOKEN과 같은 값>
```

비워두면 전송하지 않는다. **감지 기능 자체는 정상 동작하고 저장만 안 된다.**

동작 여부는 헬스체크로 확인한다.

```
GET /health
{ "status": "ok", "dictionary": 266, "llm": true, "promise": true, "spring": true }
```

`"spring": false`면 `SPRING_BASE_URL`이 비어 있는 것이다.

경로 상수는 [app/config.py](app/config.py)에 있다.

```python
SPRING_TRANSCRIPT_PATH = "/internal/v1/sessions/{session_id}/transcripts:batch"
SPRING_ALERT_PATH      = "/internal/v1/sessions/{session_id}/risk-events"
SPRING_PROMISE_PATH    = "/internal/v1/sessions/{session_id}/promises"
```

---

## 관련 코드

| | 위치 |
|---|---|
| AI 전송 | [app/sink/spring.py](app/sink/spring.py) |
| AI 핸드셰이크 | [app/server.py](app/server.py) `ws_audio` |
| Spring 수신 | `broadcast/analysis/controller/InternalAnalysisController.java` |
| Spring 저장 | `broadcast/analysis/service/AnalysisIngestService.java` |
| Spring SQL | `resources/mapper/broadcast/AnalysisMapper.xml` |
| 통합 테스트 | `broadcast/analysis/AnalysisIngestIntegrationTest.java` |

> 명세서 §13.4·13.5는 초안에서 Spring이 AI를 호출하는 방향으로 적혀 있었으나, 2026-07-31 실제 구현(AI가 Spring으로 push)에 맞춰 갱신됐다. 요청·응답 규격의 정본은 이 문서다.

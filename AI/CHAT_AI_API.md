# 채팅 AI 서버 ← Spring 호출 규격

> 대상: **백엔드**
> 음성(위험 발언) 규격은 [../BACKEND_API.md](../BACKEND_API.md) — 방향이 반대이니 주의.
> 음성은 AI가 Spring을 호출하지만, **채팅은 Spring이 AI를 호출한다.**

채팅 수집·저장·하이라이트 판정은 Spring 몫이다. AI 서버(chat-ai, 포트 **8001**)는
세 가지만 한다:

| 무엇 | 언제 | 방식 |
|---|---|---|
| **감정 분류 + 실시간 지표** (호응도·여론 분포) | 1초마다 | Spring이 배치 POST |
| **LLM 여론 요약** | 3분 주기 + 수동 버튼 | Spring이 POST |
| **반복 질문 Top N** (§3) | 10~30초 주기 | Spring이 GET 폴링 |

```
치지직 ──채팅(WS)──> Spring ──①1초 배치──> chat-ai ──감정·지표──┐
        ──시청자 폴링─>  │    ──②요약 요청──>        ──요약────┤
                         │ <───────────── 응답 ────────────────┘
                         └──> DB 저장 / 프론트 중계(SSE·WS)
```

AI 서버는 치지직에 직접 접속하지 않는다. 채팅과 시청자 수는 전부 Spring이
실어서 보내준다. **AI 서버가 죽어도 채팅 수집·저장·하이라이트는 계속되어야
한다** — 그래서 이 방향으로 설계했다 (아래 '반드시 볼 것' 1번).

## 구현 현황 (2026-07-30)

| 구간 | 상태 |
|---|---|
| §1 ingest + §4 DELETE | **양측 구현·E2E 검증 완료.** 배치 도착 시 중계는 Spring `CollectionRelayService`, 채팅 없는 초의 빈 배치 보충은 `RealtimeTicker`(1초), 어댑터·라벨 변환은 `HttpChatAiGateway`(`broadcast/realtime` 패키지). 라벨 저장 → Redis window → live-snapshot → 종료 DELETE까지 확인 |
| §2 summary | AI측 구현 완료(`server.py`). **Spring 호출부(3분 스케줄러·수동 버튼) 미구현** — API 명세서 §13.6 보류 |
| §3 questions | **양측 구현 완료 (2026-07-30).** Spring측(`broadcast/questions` 패키지): 15초 폴링(`RepeatedQuestionPoller`) → 변경분만 UPSERT(V11 스키마, dismissed 보존) → 재질문 감지 ①②③(60초 유예·+10명 재노출·리셋 방어) → live-snapshot 노출 + dismiss API + 종료 시 DELETE 직전 마지막 `GET ?limit=50` 마감까지, 파라미터는 `laive.repeated-questions.*`. AI측(`chatai/questions.py`): 라우트 + 임베딩 클러스터링 + FAQ/CONTEXT 판정 + 세대 프리픽스 questionId('생성 규칙' 준수), 단위 테스트 통과. 양측 합동 E2E는 미실시 |

> **⚠ "파이프라인이 없다"는 오해 주의.** Spring은 `CHATAI_BASE_URL`이 비어 있으면
> 기동 시 경고 로그 한 줄만 남기고 **no-op 게이트웨이**로 대체한다 — AI 호출이
> 조용히 생략되고 채팅은 `sentiment_label = NULL`로 저장되며 콘솔의 `services.ai`가
> `STOPPED`로 남는다. 코드가 없는 게 아니라 설정이 없는 것이다. 로컬에서도
> `BackEnd/spring/laive/.env`에 `CHATAI_BASE_URL=http://127.0.0.1:8001`을 넣어야 동작한다.

---

## 인증

```
Authorization: Bearer <CHATAI_TOKEN>
Content-Type: application/json
```

음성 쪽과 같은 고정 토큰(사전 공유) 방식. 값은 협의해서 정하고 양쪽 환경변수로
주입한다 (`openssl rand -hex 32` 등으로 생성, git 커밋 금지).

- **GET /health를 제외한 모든 엔드포인트**(/classify, /aggregate, /ingest, /summary,
  DELETE)에 적용된다. 불일치·누락 시 401
- AI 서버의 `CHATAI_TOKEN`이 **비어 있으면 검사하지 않는다** — 로컬 개발용.
  배포 서버에는 반드시 설정할 것
- Spring은 HTTP 클라이언트에 기본 헤더로 한 번만 등록하면 된다:
  `.defaultHeader("Authorization", "Bearer " + token)`

---

## 1. 실시간 분류·지표

```
POST /broadcasts/{broadcastId}/ingest
```

**1초마다 호출한다. 그 1초 동안 수신한 채팅을 전부 실어 보낸다.**

```json
{
  "viewers": 5458,
  "chats": [
    { "uid": "a1b2c3...", "msg": "ㅋㅋㅋ 개웃기네", "ts": 1753690000123 },
    { "uid": "d4e5f6...", "msg": "오늘 방송 몇시까지 해요?", "ts": 1753690000512 }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `viewers` | int \| null | 현재 시청자 수 (치지직 live-status 폴링값). 폴링 실패 시 null |
| `chats[].uid` | string | 치지직 userIdHash. **호응도 계산에 필수** |
| `chats[].msg` | string | 채팅 원문. **전처리하지 말 것** — AI 서버가 학습과 동일한 정규화를 내부에서 한다 |
| `chats[].ts` | long | epoch ms |

**응답:**

```json
{
  "results": [
    { "label": "웃음/재미", "score": 0.94 },
    { "label": "혼란/질문", "score": 0.91 }
  ],
  "window": {
    "windowSec": 180,
    "chatCount": 6185,
    "uniqueChatters": 1168,
    "viewers": 5458,
    "engagement": 0.214,
    "distribution": { "웃음/재미": 0.27, "중립": 0.25, "응원/호감": 0.13,
                      "환호/감탄": 0.11, "혼란/질문": 0.09, "놀람": 0.06,
                      "슬픔/아쉬움": 0.05, "분노/불만": 0.04 },
    "dominant": "웃음/재미"
  }
}
```

| 필드 | 설명 |
|---|---|
| `results` | 요청 `chats`와 **같은 순서**의 건별 감정. 원본과 함께 DB 저장용 |
| `window` | 방금 배치까지 반영된 **최근 180초** 슬라이딩 윈도우 지표 |
| `engagement` | `uniqueChatters ÷ viewers`. **"시청자의 몇 %가 최근 3분 내 채팅을 쳤나"**. viewers가 null/0이면 이 값도 null |
| `distribution` | 윈도우 여론 분포 (확률 합산 방식, 합계 1.0) |

라벨 8종: `웃음/재미` `환호/감탄` `응원/호감` `놀람` `슬픔/아쉬움` `분노/불만` `혼란/질문` `중립`

`window`를 그대로 프론트에 중계하면(SSE/WS) 실시간 호응도·여론 화면이 끝난다.
별도 조회 API가 필요 없다.

## 2. LLM 여론 요약

```
POST /broadcasts/{broadcastId}/summary
{ "mode": "AUTO" }          // 3분 주기 스케줄러는 AUTO, 프론트 버튼은 MANUAL
```

`mode`는 로그·통계 구분용이고 **규칙은 동일하다**: 자동이든 버튼이든 아래 가드를
전부 통과해야 LLM이 호출된다.

```
윈도우 채팅 30건 미만        → 422 (요약할 여론이 없음)
마지막 요약 후 60초 이내      → 409 (연속 요청 차단)
LLM 호출이 이미 진행 중       → 409 (이중 호출 차단)
```

**성공:**

```json
{
  "summary": "신곡 무대에 환호가 집중되고 있고, 음향 문제를 지적하는 반응이 일부 있습니다.",
  "dominant": "환호/감탄",
  "distribution": { "환호/감탄": 0.34, "웃음/재미": 0.22, "...": 0.0 },
  "chatCount": 4210,
  "windowSec": 180,
  "generatedAt": "2026-07-29T21:03:00Z",
  "nextAvailableAt": "2026-07-29T21:04:00Z"
}
```

**거절:**

```json
409  { "reason": "COOLDOWN", "retryAfterSec": 42, "lastSummaryAt": "..." }
409  { "reason": "IN_FLIGHT" }
422  { "reason": "NOT_ENOUGH_CHATS", "chatCount": 12, "required": 30 }
404  { "reason": "UNKNOWN_BROADCAST" }        // ingest가 들어온 적 없는 방송
```

**마지막 요약 다시 읽기 (LLM 호출 없음, 쿨다운 무관):**

```
GET /broadcasts/{broadcastId}/summary
→ 200 위와 동일 스키마 (없으면 404)
```

화면 새로고침·재입장은 반드시 GET을 쓸 것. POST는 "새로 생성"이다.

**409 IN_FLIGHT를 받으면**: 같은 방송의 요약이 지금 만들어지고 있다는 뜻이다
(자동 주기와 버튼이 겹친 경우). **재시도하지 말고 2초쯤 뒤 GET을 호출**하면
방금 완성된 요약을 받는다 — 요청자가 원한 것과 같은 내용이므로 재생성이 불필요하다.

**샘플링·표시 임계값 공유**: 요약은 `distribution` 비율이 **0.10 이상인 감정
(중립 제외)**의 채팅을 중심으로 생성된다. 프론트가 감정 칩을 표시할 때도
같은 규칙(`비율 ≥ 0.10, 중립 제외`)을 적용해야 화면과 요약이 일치한다.
이 값을 바꾸려면 AI(`chatai/config.py`의 `SUMMARY_FOCUS_THRESH`)와 프론트
상수를 **함께** 수정할 것.

## 3. 반복 질문 Top N

```
GET /broadcasts/{broadcastId}/questions?limit=5&exclude=1753690000000003,1753690000000012
```

**폴링 주기 10~30초를 권장한다.** `/ingest`(1초)와 달리 목록은 천천히 변한다.

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `limit` | 5 | 반환 개수 (최대 50) |
| `exclude` | — | **스트리머가 지운 `questionId`** 목록 (쉼표 구분). ⚠ **DB에 저장하는 구성에서는 쓰지 말 것** — 아래 '필터링 방식' 참조 |

```json
{
  "questions": [
    { "questionId": 1753690000000007, "text": "오늘 몇 시까지 하세요?", "type": "FAQ",
      "askers": 42, "messages": 51,
      "firstAskedAt": 1753690000123, "lastAskedAt": 1753693400555 }
  ],
  "totalQuestions": 1284,
  "pendingEmbedding": 23,
  "updatedAt": 1753693410000
}
```

| 필드 | 설명 |
|---|---|
| `questionId` | **long (BIGINT).** 폴링해도 안 바뀌고 재사용되지 않는다 — 아래 '생성 규칙' 참조. 삭제 기록의 기준 |
| `text` | 그 묶음에서 가장 많이 쓰인 표현 |
| `type` | `FAQ` \| `CONTEXT` \| `null`(아직 판정 전) — 아래 참조 |
| `askers` | **몇 명이 물었나** (유니크 uid). 정렬 기준이자 화면에 표시할 숫자 |
| `messages` | 총 메시지 수. `askers`와 크게 벌어지면 한 명의 도배 |
| `pendingEmbedding` | 아직 반영 안 된 대기 건수. 0이 아니면 목록이 조금 늦은 상태 |

집계 범위는 **방송 전체**다 (여론 요약의 3분 윈도우와 다르다).

### `questionId` 생성 규칙 — 단순 INCR 카운터 금지

"재사용되지 않는다"는 보장은 이 계약의 기둥이다 — Spring의 삭제 기록, UPSERT,
재노출 판정이 전부 여기에 기댄다. 그런데 집계는 Redis(TTL 12시간)에 있으므로
Redis INCR을 그대로 id로 쓰면 **Redis 유실·TTL 만료 후 카운터가 1부터 다시
시작한다.** 같은 방송에서 이미 dismissed 처리된 id가 새 질문에 다시 붙고,
Spring의 기록이 엉뚱한 질문을 숨기게 된다.

따라서 id에는 **집계 세대가 바뀔 때마다 달라지는 프리픽스**를 섞는다:

```
questionId = 집계 시작 epoch초 × 1,000,000 + 세대 내 시퀀스
예: 1753690000에 시작한 집계의 7번째 질문 → 1753690000000007
```

- Redis가 비워지고 집계가 다시 시작되면 epoch 프리픽스가 달라지므로 이전
  세대의 id와 충돌하지 않는다
- 타입은 **long (BIGINT)** — 위 예시처럼 16자리다. INT 컬럼에 넣으면 넘친다
- Spring은 값을 해석하지 말고 불투명한 식별자로만 다룰 것 (형식은 바뀔 수 있다)

### `type` — 답할 수 있는 질문인가

```
FAQ     화면을 안 봐도 답할 수 있다
        "내일 야방있음?" "오늘 2부는 메이플인가요?" "데바데도 브레이킹 있음?"
CONTEXT 지금 화면이나 직전 대화를 알아야 한다
        "저게 안맞아?" "아니 이거 뭐야" "왼쪽 3개?"
null    아직 판정 전 (곧 채워진다)
```

**AI는 태그만 붙이고 거르지 않는다.** 필터는 프론트에서 "전체 / FAQ만" 토글로
주는 것을 권한다 — 판정이 틀렸을 때 스트리머가 원본을 볼 수 있어야 한다.

⚠ **FAQ는 소수다.** 게임 방송 실측에서 상위 30개 중 13개 정도였다. 시청자는
화면을 보며 반응하지 FAQ를 던지지 않기 때문이다. **"FAQ만" 화면을 5개로 채우려면
`limit=30` 정도로 넉넉히 받아 거르는 편이 안전하다.**

### 삭제(답변 완료)는 Spring이 기억한다

**AI는 무엇이 지워졌는지 저장하지 않는다.** 스트리머의 결정은 비즈니스 상태이고,
AI의 Redis는 TTL 기반이라 재시작하면 지운 질문이 되살아난다.

**AI는 답변 여부를 모르므로 계속 센다.** 이건 의도된 것이다 — 재질문 감지가
공짜로 따라온다.

```
14:00  "내일 야방있음?"  askers 12
14:05  스트리머 답변 → Spring이 dismissed 기록. AI는 모름
16:00  같은 질문         askers 31   ← 19명이 더 물었다 = 답이 안 닿았다
```

#### ⚠ 답변 직후 60초는 기준을 잡지 말 것 (송출 지연)

답변을 해도 시청자에게는 늦게 도달한다. **그동안 같은 질문이 계속 들어온다.**

```
T+0초   답변 시작
T+5초   시청자 도달 (송출 지연)
T+10초  듣고 이해
T+15초  이미 타이핑 중이던 사람들이 엔터
```

답변 시점의 `askers`를 기준으로 삼으면 **답하자마자 "재질문 발생"이 뜬다.**
기준값은 유예가 끝난 뒤에 기록해야 한다.

```sql
dismissed_at       TIMESTAMPTZ  -- 답변 처리 시각
askers_at_dismiss  INT          -- 답변 시점 askers (②의 fallback)
baseline_askers    INT          -- 유예 종료 후 기록 (NULL이면 아직 미확정)
```

```
① 답변 완료      dismissed_at = now, askers_at_dismiss = 현재 askers,
                 baseline_askers = NULL
② 폴링마다       if baseline_askers IS NULL and now - dismissed_at >= 60초:
                     baseline_askers = 이번 응답의 askers   ← 여기서 확정
                     (응답에 그 질문이 없으면 askers_at_dismiss로 확정)
③ 재노출 판정    if baseline_askers IS NOT NULL:
                     if 현재 askers < baseline_askers:      ← 집계 리셋 방어
                         baseline_askers = 현재 askers      (이번 판정은 건너뜀)
                     elif 현재 askers - baseline_askers >= 10:
                         다시 보여준다
```

**②의 fallback이 필요한 이유**: 답변 처리 후 질문이 top-N 밖으로 밀려나면 폴링
응답에 안 나와서 baseline이 영영 NULL로 남는다. 그 상태로 두면 한참 뒤 재질문이
불어나 top-N에 재진입한 시점의 — 이미 급증한 — askers가 baseline이 되어 **정작
감지해야 할 급증을 놓친다.** `askers_at_dismiss`로 확정하면 유예 구간의 관성
유입이 재질문으로 조금 과계산되지만, +10 임계가 흡수하고 놓치는 것보다 낫다.

**③의 리셋 방어**: 집계가 리셋되면(Redis 유실, 키 만료 등) askers가 baseline보다
작아질 수 있다. 그대로 빼면 음수 diff로 판정이 조용히 죽으므로 baseline을 현재
값으로 재확정하고 넘어간다.

유예 60초는 송출 지연(4~5초) + 인지 시간 + 타이핑 중이던 사람을 덮는 값이다.
짧으면 오탐이 남고, 길어도 재노출 임계(+10명)를 넘으려면 어차피 시간이 걸린다.
**유예 60초와 임계 +10명은 문서 끝 '파라미터 기본값' 표에 있다.** 특히 +10명은
중간 규모 채널 실측 기준이라 **시청자 수백 명대 채널에서는 도달하기 어렵다** —
규모별 조정(예: 시청자 수 비례)이 필요하면 협의할 것.

#### 필터링 방식 — DB에 저장한다면

`exclude` 파라미터로 AI에게 제외를 맡길 수도 있지만, **`repeated_questions`에
저장한다면 쓰지 않는 편이 낫다.**

```
GET /questions?limit=50&exclude=…03,…07   →  3·7번 질문이 응답에 없음
Spring이 전량 교체                          →  DB에서도 사라짐
                                            →  답변해준 질문이 세션 리포트에서 누락
```

권장:

```
AI 호출   GET /questions?limit=50        (exclude 없이 전량)
DB        UPSERT (session_id, question_id)  — dismissed 플래그는 보존
화면      DB에서 dismissed = false 필터
```

전량 교체(DELETE + INSERT)가 아니라 **UPSERT**여야 `dismissed`가 살아남는다.
`questionId`를 재사용하지 않게 만든 이유가 여기에 있다.

### 알아둘 것

- **최대 30초 지연.** 임베딩을 배치로 처리한다. `/ingest` 안에서 부르면
  GMS 호출(500건 1.9초)이 계약 타임아웃 2초를 먹는다
- `askers`가 **3명 미만이면 목록에 안 나온다** (잡음 제거)
- `GMS_KEY`가 없으면 빈 목록을 반환한다 — 나머지 기능은 정상
- **404**: `ingest`가 들어온 적 없는 방송. **빈 목록(200)**: 아직 안 모임
- 클러스터 상한(2,000개)을 넘으면 `askers`가 적은 것부터 사라진다. 그 `questionId`는
  **재사용되지 않으므로** Spring의 기록이 엉뚱한 질문을 가리킬 일은 없다
- Redis TTL 12시간. **세션 리포트는 DB에 저장된 값을 써야 한다**

### DB 저장 시 쓰기 부담

`limit=50` 폴링이면 세션당 행 수는 100~300행(약 60KB)으로 작다. 문제는 처리량이
아니라 **의미 없는 UPDATE**다.

```
10초 폴링 × 5시간 × 50행 = 90,000 UPSERT
그중 값이 실제로 바뀌는 건 극히 일부
```

PostgreSQL은 UPDATE마다 죽은 튜플을 남기므로 autovacuum 부담이 된다. 둘 중
하나만 해도 충분하다:

- **변경분만 UPSERT** — 직전 폴링 결과와 비교해 `askers`·`type`이 바뀐 행만
  (90,000 → 수백 건)
- **폴링 30초** — 10초 대비 1/3

## 4. 방송 종료

```
DELETE /broadcasts/{broadcastId}
→ 204
```

윈도우·요약 캐시·질문 집계를 정리한다. 방송 종료 시 호출해주면 되고,
**깜빡해도 된다** — TTL이 알아서 정리한다 (윈도우 10분, 질문 집계 12시간).

**질문을 DB에 저장한다면, DELETE 전에 `GET /questions?limit=50`을 마지막으로
한 번 호출해 최종 수치로 마감할 것.** DB의 askers는 각 질문이 마지막으로
폴링에 목격된 시점의 값이다 — 마감 없이 리포트를 만들면 상위권 질문조차 최대
폴링 주기(30초)만큼 낡은 값으로 남는다. top-50 밖으로 밀려난 질문은 이 마감으로도
갱신되지 않으므로, 세션 리포트에는 '종료 시점 상위 50개 기준'임을 밝혀 둘 것.

---

## ⚠ 구현 시 반드시 볼 것

### 1. ingest 실패 시 기다리지 말 것 — 이 설계의 존재 이유

AI 서버가 느리거나 죽었을 때 **채팅 파이프라인이 같이 멈추면 안 된다.**

- 타임아웃 짧게 (권장 2초)
- 실패하면 **재시도하지 말고 그 배치는 건너뛴다.** 원본은 감정 없이 저장
- 감정은 나중에 저장된 원본으로 재분류(backfill)할 수 있다. **원본 유실만이 복구 불가**

요약(POST /summary)도 동일 — 실패하면 다음 주기에 다시 시도하면 된다.

### 2. 빈 배치도 1초마다 보내라

채팅이 없어도 `{ "viewers": 5458, "chats": [] }`를 보내야 한다.

- 윈도우가 시간에 따라 미끄러진다 (오래된 채팅이 빠짐)
- viewers가 갱신된다

안 보내면 조용한 채널의 호응도가 **마지막 값에 얼어붙은 것처럼 보인다.**

### 3. 409·422는 오류가 아니다

요약 거절은 정상 동작이다. 로그에 에러로 찍지 말 것.

- 422 (채팅 부족): 다음 주기에 자연 해소
- 409 (쿨다운): 응답의 `retryAfterSec` 만큼 기다리면 됨

프론트 버튼은 성공 응답의 `nextAvailableAt`으로 **비활성화 + 카운트다운**을
구현하면 사용자가 거절을 볼 일 자체가 없다.

### 4. 윈도우는 팀 Redis(DB 1)에 있다 — 재시작해도 지표가 이어진다

윈도우 저장소는 `BackEnd/spring/laive/compose.yaml`의 **팀 Redis를 공유**한다.
Spring이 DB 0을 쓰고 chat-ai는 **DB 1**을 쓰므로 키가 섞이지 않는다
(RedisInsight :5540에서 확인 가능, 키 접두사 `chat:`).

- AI 서버를 재배포/재시작해도 방송 중 지표가 끊기지 않는다
- 단, **Redis 자체가 내려가면** ingest가 503을 반환한다 → '반드시 볼 것 1'대로
  그 배치는 건너뛰면 된다 (원본은 Spring에 있으므로 유실 없음)
- 방송별 키는 TTL(5~10분)이 있어 DELETE를 못 받아도 스스로 사라진다

### 5. 응답 코드 의미

| 응답 | Spring 동작 |
|---|---|
| `2xx` | 정상 |
| `409` `422` | 정상 거절 (위 3번) |
| `4xx` (그 외) | 요청 형식 문제 — 재시도 무의미, 로그 후 버림 |
| `5xx` / 타임아웃 | ingest는 건너뜀 (1번), summary는 다음 주기 |

---

## AI 서버 설정

```
CHATAI_TOKEN=<협의한 토큰>          # 비우면 인증 검사 안 함 (로컬 개발용)
CHATAI_MODEL_DIR=models/kcelectra-chat-8emo-onnx
CHATAI_REDIS_URL=redis://127.0.0.1:6379/1   # 팀 Redis의 DB 1 (기본값)
GMS_KEY=<SSAFY GMS 키>              # /summary(LLM) · /questions(임베딩) 공용
```

동작 확인:

```
GET /health
{ "status": "ok", "model": "...", "labels": [...8종...], "redis": true }
```

`redis: false`면 /ingest가 503을 낸다 — Redis 컨테이너부터 확인할 것
(`cd BackEnd/spring/laive && docker compose up -d redis`).

## 파라미터 기본값 (협의로 변경 가능)

| 항목 | 값 | 위치 |
|---|---|---|
| 윈도우 길이 | 180초 | chatai/config.py |
| 요약 최소 채팅 | 30건 | 〃 |
| 요약 쿨다운 | 60초 | 〃 |
| 유휴 자동 정리 | 마지막 ingest 후 10분 | 〃 |
| 질문 노출 최소 인원 | 3명 | 〃 |
| 질문 임베딩 주기 | 100건 또는 30초 | 〃 |
| 질문 집계 보관 | 12시간 | 〃 |
| 재질문 판정 유예 | 답변 후 60초 | Spring 상수 |
| 재질문 재노출 임계 | +10명 | Spring 상수 (소규모 채널은 조정 필요) |

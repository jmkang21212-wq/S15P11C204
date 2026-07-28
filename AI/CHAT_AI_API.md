# 채팅 AI 서버 ← Spring 호출 규격

> 대상: **백엔드**
> 음성(위험 발언) 규격은 [../BACKEND_API.md](../BACKEND_API.md) — 방향이 반대이니 주의.
> 음성은 AI가 Spring을 호출하지만, **채팅은 Spring이 AI를 호출한다.**

채팅 수집·저장·하이라이트 판정은 Spring 몫이다. AI 서버(chat-ai, 포트 **8001**)는
두 가지만 한다:

| 무엇 | 언제 | 방식 |
|---|---|---|
| **감정 분류 + 실시간 지표** (호응도·여론 분포) | 1초마다 | Spring이 배치 POST |
| **LLM 여론 요약** | 3분 주기 + 수동 버튼 | Spring이 POST |

```
치지직 ──채팅(WS)──> Spring ──①1초 배치──> chat-ai ──감정·지표──┐
        ──시청자 폴링─>  │    ──②요약 요청──>        ──요약────┤
                         │ <───────────── 응답 ────────────────┘
                         └──> DB 저장 / 프론트 중계(SSE·WS)
```

AI 서버는 치지직에 직접 접속하지 않는다. 채팅과 시청자 수는 전부 Spring이
실어서 보내준다. **AI 서버가 죽어도 채팅 수집·저장·하이라이트는 계속되어야
한다** — 그래서 이 방향으로 설계했다 (아래 '반드시 볼 것' 1번).

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

## 3. 방송 종료

```
DELETE /broadcasts/{broadcastId}
→ 204
```

윈도우·요약 캐시를 정리한다. 방송 종료 시 호출해주면 되고, **깜빡해도 된다** —
마지막 ingest 후 10분이 지나면 AI 서버가 알아서 정리한다.

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
GMS_KEY=<SSAFY GMS 키>              # /summary 용
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

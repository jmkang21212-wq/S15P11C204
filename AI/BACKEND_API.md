# AI 서버 → Spring 전송 규격

> 대상: **백엔드**
> 프론트엔드용 문서는 [FRONTEND_API.md](FRONTEND_API.md)에 따로 있다.

AI 서버가 방송 중 실시간으로 세 가지를 Spring에 보낸다. **Spring은 받아서 저장하는 쪽**이고, AI 서버가 호출하는 엔드포인트 세 개를 만들어주면 된다.

| 무엇 | 언제 | 방식 |
|---|---|---|
| **전사** (모든 발화) | 2초마다 또는 10건 모이면 | 배치 POST |
| **위험 경고** | 감지 즉시 | 개별 POST |
| **약속** | 감지 즉시 | 개별 POST |

```
프론트 ──오디오(WS)──> AI 서버 ──POST──> Spring ──> DB
      <──경고(WS)────
                        이 문서가 다루는 구간 ↑
```

프론트엔드는 이 경로에 관여하지 않는다. 프론트는 AI 서버와 WebSocket으로 경고만 주고받고, **저장은 전적으로 이 경로**로 이뤄진다.

방송 종료 리포트는 여기 쌓인 데이터를 Spring이 조회해서 만든다. 리포트 조회 API는 Spring 내부 설계이므로 이 문서 범위 밖이다.

---

## 인증

```
Authorization: Bearer <SPRING_TOKEN>
Content-Type: application/json
```

서버 간 통신이라 고정 토큰을 쓴다. 토큰 값은 협의해서 정하면 되고, AI 서버는 환경변수로 받는다.

---

## 1. 전사 저장

```
POST /api/ai/transcripts
```

```json
{
  "items": [
    {
      "sessionId": "user42-3f9a2b",
      "seq": 1,
      "text": "그래서 이번 주까지 끝내야 합니다",
      "startedAt": "2026-07-27T07:06:41.000Z",
      "endedAt": "2026-07-27T07:06:42.500Z",
      "forced": false
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `sessionId` | string | 방송 세션 식별자 |
| `seq` | int | 세션 내 발화 순번 (1부터) |
| `text` | string | 전사된 발화 내용 |
| `startedAt` `endedAt` | ISO8601 UTC | 발화 구간. `Instant.parse()`로 바로 파싱된다 |
| `forced` | boolean | `true`면 6초 제한으로 잘린 발화. 다음 `seq`로 이어짐 |

`items`는 항상 배열이고 보통 1~10개다.

## 2. 위험 경고 저장

```
POST /api/ai/alerts
```

**사전매칭** (명백한 비속어, 발화 후 약 0.4초)

```json
{
  "sessionId": "user42-3f9a2b",
  "seq": 10,
  "source": "DICTIONARY",
  "severity": "HIGH",
  "category": "profanity",
  "text": "아 씨발 병신아",
  "startedAt": "2026-07-27T07:06:50.000Z",
  "endedAt": "2026-07-27T07:06:51.500Z",
  "reason": null,
  "matches": [
    { "word": "씨발", "severity": "HIGH", "category": "profanity", "start": 2, "end": 4 },
    { "word": "병신", "severity": "HIGH", "category": "insult",    "start": 5, "end": 7 }
  ]
}
```

**LLM 판정** (문맥 기반, 발화 후 약 2.2초)

```json
{
  "sessionId": "user42-3f9a2b",
  "seq": 11,
  "source": "LLM",
  "severity": "MEDIUM",
  "category": "insult",
  "text": "너 진짜 대가리가 꽃밭이구나",
  "startedAt": "2026-07-27T07:06:51.000Z",
  "endedAt": "2026-07-27T07:06:53.000Z",
  "reason": "상대를 직접 겨냥한 비하 표현이 포함되어 있음",
  "matches": []
}
```

**두 종류가 같은 스키마다.** 테이블 하나로 받으면 된다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `source` | enum | `DICTIONARY` \| `LLM` |
| `severity` | enum | `HIGH` \| `MEDIUM` \| `LOW` |
| `category` | string | `profanity` `insult` `threat` `harassment` `discrimination` |
| `text` | string | 해당 발화 전문 (전사 테이블과 중복되지만 조회 편의를 위해 포함) |
| `reason` | string \| null | LLM 판정 근거. 사전매칭은 항상 `null` |
| `matches` | array | 사전에 걸린 단어들. LLM은 항상 빈 배열 |

`matches[].start`/`end`는 `text` 안의 문자 인덱스다. 리포트에서 하이라이트할 때 쓴다.

---

## 3. 약속 저장

```
POST /api/ai/promises
```

스트리머가 시청자에게 한 약속을 감지하면 **건별로 즉시** 보낸다. 방송당 많아야 수십 건이다.

```json
{
  "sessionId": "user42-3f9a2b",
  "seq": 120,
  "content": "공포게임 방송",
  "rawText": "2주 안으로 공포게임 할게요",
  "deadlineText": "2주 안으로",
  "deadlineAt": "2026-08-11T07:06:51.000Z",
  "condition": null,
  "confidence": 0.98,
  "spokenAt": "2026-07-28T07:06:51.000Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `content` | string | 약속 내용을 간결한 명사구로 정리한 것 |
| `rawText` | string | 발화 원문 |
| `deadlineText` | string \| null | 기한 표현 원문 (`"2주 안으로"`) |
| `deadlineAt` | ISO8601 \| null | **해석된 절대 시각** |
| `condition` | string \| null | 조건부 약속의 조건 |
| `confidence` | float | 0.6~1.0. 그 미만은 AI가 걸러서 보내지 않는다 |
| `spokenAt` | ISO8601 | 발화 시각 |

### 기한은 이미 계산해서 보낸다

`"2주 안으로"`, `"다음 주"` 같은 한국어 표현을 **Spring이 파싱할 필요가 없다.** 발화 시각을 아는 AI 서버가 절대 시각으로 바꿔서 `deadlineAt`에 담아 보낸다. 그대로 저장하면 마감 임박 알림이나 이행 여부 판정에 바로 쓸 수 있다.

`deadlineText`는 화면에 원래 표현을 그대로 보여주고 싶을 때 쓰라고 같이 보낸다.

### 세 가지 경우가 있다

| 상황 | `deadlineAt` | `condition` | 예시 |
|---|---|---|---|
| 기한 있음 | 값 있음 | `null` | "2주 안으로 공포게임 할게요" |
| 조건부 | `null` | 값 있음 | "구독자 1000명 되면 얼굴 공개할게요" |
| 기한 없음 | `null` | `null` | "다음에 꼭 스피드런 할게요" |

**조건부 약속은 날짜를 정할 수 없다.** 마감일 기준으로만 추적하는 스키마를 짜면 이 케이스가 누락되니, `condition`이 있는 약속은 별도 처리 경로가 필요하다.

### 중복은 AI가 거른다

스트리머가 같은 약속을 여러 번 말하거나 발화가 6초에서 잘려 두 번 잡히는 경우가 있는데, **15분 창 안에서는 한 번만 보낸다.** 다만 방송이 길어지면 같은 약속이 다시 올 수 있으므로, `content` 기준 멱등 처리를 해두면 안전하다.

---

## ⚠ 구현 시 반드시 볼 것

### 1. 경고가 전사보다 먼저 도착한다

전사는 **최대 2초 모았다가** 보내고, 경고는 **즉시** 보낸다. 그래서 순서가 뒤집힌다.

```
t=0.4s  경고 도착 (seq=10)
t=2.0s  전사 도착 (seq=10 포함)
```

**경고 테이블이 전사 테이블을 외래키로 참조하면 저장이 실패한다.** 두 테이블을 독립적으로 저장하고, 조회할 때 `(sessionId, seq)`로 조인하는 방식을 권장한다.

### 2. 같은 데이터가 두 번 올 수 있다

네트워크 오류나 타임아웃 시 AI 서버가 재시도한다. Spring이 실제로는 저장에 성공했는데 응답이 유실되면 **중복 요청이 들어온다.**

멱등 처리를 권장한다.

- 전사: `(sessionId, seq)` 유니크
- 경고: `(sessionId, seq, source)` 유니크

같은 키로 들어오면 무시하거나 덮어쓰면 된다.

### 3. 응답 코드에 따라 동작이 다르다

| 응답 | AI 서버 동작 |
|---|---|
| `2xx` | 성공 처리 |
| `4xx` | **재시도하지 않고 버린다.** 요청 자체가 잘못됐다고 보기 때문 |
| `5xx` / 타임아웃 | 0.5 → 1 → 2초 간격으로 최대 3회 재시도 |

**저장에 일시적으로 실패했다면 반드시 5xx를 반환해야 한다.** 4xx를 주면 그 데이터는 영영 사라진다.

### 4. seq는 세션 안에서만 유일하다

`seq`는 세션마다 1부터 시작한다. 전역 유일 키가 아니므로 **반드시 `sessionId`와 묶어서** 다뤄야 한다.

---

## AI 서버 설정

`.env`에 아래를 채우면 전송이 켜진다.

```
SPRING_BASE_URL=http://spring-server:8080
SPRING_TOKEN=<협의한 토큰>
```

비워두면 전송을 하지 않는다. **감지 기능 자체는 정상 동작하고 저장만 안 된다.** 스펙 합의 전까지는 비워두면 된다.

동작 여부는 헬스체크로 확인할 수 있다.

```
GET /health
{ "status": "ok", "dictionary": 38, "llm": true, "spring": true }
```

## 엔드포인트 경로 변경

경로는 [app/config.py](app/config.py)에 있다. 백엔드 컨벤션에 맞춰 바꾸면 된다.

```python
SPRING_TRANSCRIPT_PATH = "/api/ai/transcripts"
SPRING_ALERT_PATH = "/api/ai/alerts"
SPRING_PROMISE_PATH = "/api/ai/promises"
```

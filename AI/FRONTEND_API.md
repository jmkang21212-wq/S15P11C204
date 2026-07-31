# 프론트엔드 ↔ AI 서버 연동 규격

> 대상: **프론트엔드**
> 백엔드용 문서는 [BACKEND_API.md](BACKEND_API.md)에 따로 있다.

마이크 오디오를 WebSocket으로 보내면, **위험 발언 경고**와 **약속 감지 결과**가 돌아온다.

동작하는 예제가 [static/client.html](static/client.html)에 있다. 오디오 캡처 부분은 그대로 복사해 써도 된다.

---

## 0. 역할 분담 — 프론트가 안 해도 되는 것

```
프론트 ──오디오(WS)──> AI 서버 ──전사/판정──> Spring ──> DB
      <──경고(WS)────                          ↑
                                        프론트는 여기에 관여하지 않는다
```

**전사 내용과 위험 감지 결과를 DB에 저장하는 것은 AI 서버가 Spring으로 직접 보낸다.** 프론트는 저장 API를 호출할 필요가 없다.

프론트가 받는 것은 **위험 경고와 약속 감지, 두 가지뿐이다.** 모든 발화의 전사는 오지 않는다. 각 이벤트 안에 해당 발화의 원문이 들어있어 화면 표시에는 그것으로 충분하다.

방송 종료 리포트를 만들 때는 **Spring의 조회 API**를 쓴다. AI 서버와는 무관하다.

### 요약

| 프론트가 하는 것 | 프론트가 안 하는 것 |
|---|---|
| 마이크 캡처 → WS로 전송 | 전사 저장 |
| 경고·약속 이벤트 받아서 표시 | 감지 내역·약속 저장 |
| 방송 시작/종료 시 연결 관리 | 리포트용 데이터 관리 |

---

## 1. 연결

```
ws://<host>:8000/ws/audio      개발
wss://<host>/ws/audio          운영
```

마이크 접근은 보안 컨텍스트가 필요하다. `localhost`가 아니면 **HTTPS + wss 필수**다.

## 2. 핸드셰이크

연결 직후 **첫 메시지는 반드시 JSON 텍스트**여야 한다. 10초 안에 안 보내면 서버가 끊는다.

```json
{ "session_id": "user42-3f9a2b", "token": "..." }
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `session_id` | O | 이 스트림의 식별자 |
| `token` | △ | 인증 토큰 |
| `transcripts` | X | `true`면 모든 발화의 전사도 받는다. **개발용.** 기본 `false` |

### session_id 만드는 규칙 (중요)

**같은 `session_id`로 두 번 접속하면 나중 연결이 앞 연결을 덮어쓴다.** 앞 탭은 연결이 살아있는데 경고가 안 오는 상태가 되고, 원인 찾기가 매우 어렵다.

유저 ID만 쓰면 안 된다. **탭을 두 개 열면 바로 충돌한다.**

```js
// 나쁜 예 — 두 탭 열면 충돌
const sessionId = `user${userId}`;

// 좋은 예 — 접속할 때마다 새로 생성
const sessionId = `user${userId}-${crypto.randomUUID().slice(0, 8)}`;
```

> `token`은 현재 검증하지 않는다. Spring JWT 검증으로 교체 예정이므로 미리 넣어두면 나중에 프론트를 안 고쳐도 된다.

성공하면 서버가 응답한다.

```json
{ "type": "ready", "session_id": "...", "sample_rate": 16000, "chunk": 512 }
```

**이 메시지를 받은 뒤에** 오디오를 보내기 시작한다.

## 3. 오디오 전송

`ready` 이후 **바이너리 프레임**으로 계속 보낸다.

| 항목 | 값 |
|---|---|
| 샘플레이트 | **16000 Hz** |
| 채널 | 모노 (1채널) |
| 형식 | 16bit 부호있는 정수 (PCM16) |
| 바이트 순서 | 리틀엔디안 |

**샘플레이트가 제일 중요하다.** 서버 VAD가 16kHz 고정이라 48kHz를 보내면 전부 깨진다. 에러도 안 나고 그냥 아무것도 감지되지 않으므로 반드시 확인해야 한다.

한 번에 보내는 크기는 자유다. 서버가 512샘플 단위로 알아서 재조립한다.

```js
// 16kHz 요청. 브라우저가 거부할 수 있으니 반드시 확인할 것.
const ctx = new AudioContext({ sampleRate: 16000 });
if (ctx.sampleRate !== 16000) console.warn('샘플레이트 불일치', ctx.sampleRate);

const stream = await navigator.mediaDevices.getUserMedia({
  audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
});

// AudioWorklet 안에서 Float32 -> Int16 변환
const out = new Int16Array(input.length);
for (let i = 0; i < input.length; i++) {
  const s = Math.max(-1, Math.min(1, input[i]));
  out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
}
ws.send(out.buffer);
```

## 4. `alert` — 위험 발언 경고

**발화 하나당 최대 하나만** 온다.

공통 필드는 아래와 같고, `source`에 따라 추가 필드가 붙는다.

| 필드 | 항상 있음 | 설명 |
|---|---|---|
| `type` | O | 항상 `"alert"` |
| `source` | O | `"dictionary"` \| `"llm"` |
| `seq` | O | 세션 내 발화 순번 |
| `severity` | O | `high` \| `medium` \| `low` |
| `category` | O | `profanity` `insult` `threat` `aggressive` `sexual` `harassment` `discrimination` |
| `text` | O | 해당 발화 전문 |
| `start_ts` `end_ts` | O | 발화 구간 (epoch 초) |
| `hits` | dictionary만 | 걸린 단어 목록 |
| `reason` | llm만 | 판정 근거 한 문장 |

**`severity`는 두 종류 모두 같은 필드명이다.** `source`로 분기하지 않고 그냥 `m.severity`를 쓰면 된다.

**`severity`는 "위험한지"가 아니라 "얼마나 심한지"다.** 위험이 아닌 발화는 애초에
오지 않으므로, 도착한 alert는 전부 실제 감지 결과다. 화면 강조 수준을 나누는 데 쓰면 된다.

| severity | 뜻 | 예 |
|---|---|---|
| `high` | 심한 욕설, 직접적 위협, 노골적 성적 표현 | 씨발, 개새끼, 죽여버린다 |
| `medium` | 분명한 욕설·모욕 | 지랄, 등신, 미친놈 |
| `low` | 가벼운 비속어나 거친 말 | 닥쳐, 꺼져 |

`category`는 `source`에 따라 나오는 값이 다르다. 모르는 값이 와도 죽지 않게
기본 처리를 두는 편이 안전하다.

| category | dictionary | llm | 설명 |
|---|---|---|---|
| `profanity` | O | O | 비속어, 욕설 |
| `insult` | O | O | 특정인을 향한 모욕 |
| `threat` | O | O | 위협, 협박 |
| `aggressive` | O | - | 공격적인 말투 |
| `sexual` | O | O | 성적 표현, 성드립 |
| `harassment` | - | O | 특정인을 향한 성희롱, 집요한 괴롭힘 |
| `discrimination` | - | O | 차별 발언 |

### `source: "dictionary"` — 사전매칭

명백한 비속어. 발화가 끝나고 약 **0.4초** 뒤 도착한다.

```json
{
  "type": "alert",
  "source": "dictionary",
  "seq": 12,
  "severity": "high",
  "category": "profanity",
  "text": "아 씨발 병신아",
  "start_ts": 1753600000.12,
  "end_ts": 1753600002.45,
  "hits": [
    { "matched": "씨발", "severity": "high", "category": "profanity", "start": 2, "end": 4 },
    { "matched": "병신", "severity": "high", "category": "insult",    "start": 5, "end": 7 }
  ]
}
```

한 발화에 여러 단어가 걸릴 수 있어 `hits`는 **배열**이다. 최상위의 `severity`/`category`는 그중 **가장 심각한 것**이다. 간단히 표시하려면 최상위 값만 쓰고, 자세히 보여주려면 `hits`를 펼치면 된다.

`hits[].start`/`end`는 `text` 안의 문자 인덱스다. 해당 구간만 하이라이트할 수 있다.

### `source: "llm"` — LLM 판정

사전에 없는 표현을 문맥으로 판단한 것. 발화 후 약 **2.2초** 뒤 도착한다.

```json
{
  "type": "alert",
  "source": "llm",
  "seq": 12,
  "severity": "medium",
  "category": "insult",
  "reason": "상대를 직접 겨냥한 비하 표현이 포함되어 있음",
  "text": "너 진짜 대가리가 꽃밭이구나",
  "start_ts": 1753600000.12,
  "end_ts": 1753600002.45
}
```

`reason`은 판정 근거 한 문장이다. 방송자에게 왜 경고인지 보여줄 때 쓴다. `hits`는 없다.

> LLM이 위험하지 않다고 판정한 발화는 전송하지 않는다. 정상 발화가 대부분이므로 다 보내면 화면이 무의미한 메시지로 덮인다.

## 5. `promise` — 약속 감지

스트리머가 시청자에게 한 약속을 감지하면 온다. 발화 후 약 **2초** 뒤 도착한다.

```json
{
  "type": "promise",
  "seq": 120,
  "promise_id": 4821,
  "content": "공포게임 방송",
  "text": "2주 안으로 공포게임 할게요",
  "deadline_text": "2주 안으로",
  "deadline_at": 1786534981.2,
  "condition": null,
  "confidence": 0.98,
  "spoken_at": 1785325981.2
}
```

| 필드 | 설명 |
|---|---|
| `promise_id` | Spring `PROMISES`의 행 id. **확정/무시를 호출할 때 쓴다.** 저장 실패 시 `null` |
| `content` | 약속 내용을 간결하게 정리한 것. 화면에 이걸 보여주면 된다 |
| `text` | 발화 원문 |
| `deadline_text` | 기한 표현 원문 (`"2주 안으로"`). 없으면 `null` |
| `deadline_at` | **해석된 절대 시각** (epoch 초). 기한이 없거나 조건부면 `null` |
| `condition` | 조건부 약속의 조건 (`"구독자 1000명 되면"`). 아니면 `null` |
| `confidence` | 0.0~1.0. 0.6 미만은 애초에 보내지 않는다 |

기한은 AI 서버가 발화 시각 기준으로 **절대 시각까지 계산해서** 보낸다. 프론트가 `"2주 안으로"`를 파싱할 필요가 없다.

조건부 약속(`"구독자 1000명 되면"`)은 날짜를 정할 수 없으므로 `deadline_at`이 `null`이고 `condition`에 내용이 들어간다. **둘 다 `null`이면 기한 없는 약속**이다(`"다음에 꼭 할게요"`).

> 같은 약속을 반복해서 말해도 15분 안에는 한 번만 온다. 중복 처리는 서버가 한다.

**`promise_id`로 확정/무시를 붙인다.** AI 서버는 약속을 Spring에 먼저 저장하고, 돌려받은 행 id를 이 이벤트에 실어 보낸다. 프론트는 이 값을 그대로 Spring의 `/api/v1/promises/{id}` 계열 API에 넘기면 된다. AI 서버에 직접 확정/무시를 보내는 경로는 없다.

`promise_id`가 `null`인 경우가 있다. Spring 저장이 꺼져 있거나(핸드셰이크에 `broadcastSessionId`를 안 넣은 경우) 저장에 실패한 경우다. 이때는 카드는 그대로 띄우되 확정/무시 버튼을 비활성화해야 한다. 가리킬 행이 없기 때문이다.

> Spring이 응답을 늦게 주면 이 이벤트도 그만큼 늦게 도착한다(최악 수 초). id를 먼저 확보해야 해서 생기는 지연이고, `alert`와 오디오 처리에는 영향이 없다.

## 6. UI 구현

이벤트를 받아 목록에 추가하기만 하면 된다. 경고 하나가 카드 하나다.

```js
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.type === 'alert') showAlert(m);
  else if (m.type === 'promise') showPromise(m);
};
```

**도착 시점이 종류마다 다르다.**

| 이벤트 | 발화 후 |
|---|---|
| `alert` (사전) | 0.4초 |
| `alert` (LLM) | 2.2초 |
| `promise` | 2초 |

방송자가 말한 직후 화면이 조용하다고 안전한 것이 아니다. 2초쯤 뒤에 뜰 수 있다.

## 7. 연결 생명주기

| 시점 | 동작 |
|---|---|
| 방송 시작 | WS 연결 → 핸드셰이크 → `ready` 받고 오디오 전송 시작 |
| 방송 중 | 오디오 계속 전송 (무음도 그냥 보내면 된다. 서버가 걸러낸다) |
| 방송 종료 | `ws.close()` + 마이크 트랙 정지 |

**무음을 걸러서 보낼 필요 없다.** 서버 VAD가 말하는 구간만 골라내므로 그냥 계속 흘려보내면 된다.

**연결이 끊기면** 자동 재연결을 권장한다. 이때 `session_id`는 **새로 만들어야 한다.**

## 8. 종료 코드

| 코드 | 의미 |
|---|---|
| `1008` | 핸드셰이크 누락 / JSON 형식 오류 / 인증 실패 |
| `1000` | 정상 종료 |

## 9. 서버 상태 확인

```
GET /health
```

```json
{ "status": "ok", "dictionary": 38, "llm": true, "promise": true, "spring": true }
```

`llm`이 `false`면 LLM 판정이 꺼진 상태다(사전매칭 경고만 온다). `promise`가 `false`면 약속 감지가 꺼져 있다.

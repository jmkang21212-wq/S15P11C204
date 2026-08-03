# 하이라이트 클립 — 프론트 작업 명세

> 전체 설계는 [../CLIP_SPEC.md](../CLIP_SPEC.md). 이 문서는 **Electron이 할 일만** 추렸다.
>
> **백엔드는 구현·테스트 완료 상태다.** 아래 API는 전부 실제로 도는 것이고,
> 요청·응답 형태는 구현에서 그대로 옮겼다.

방송이 끝나면 서버가 하이라이트 구간 5개를 골라준다.
**우리 앱이 OBS 로컬 녹화본에서 그 구간을 잘라 서버에 올린다.**
서버는 영상을 녹화하지 않는다 — 원본은 스트리머 PC에만 있다.

---

## 작업 3개

### A. 녹화 시작·종료 (`src/main/obs.ts`)

이미 `StartStream`/`StopStream`이 있다. 옆에 녹화를 붙인다.

```
방송 시작 → obs.call('StartStream')      (기존)
          → obs.call('StartRecord')      (추가)
          → POST /broadcast-sessions/{id}/recording

방송 종료 → obs.call('StopRecord')       (추가)
          → 응답의 outputPath 확보
          → PATCH /broadcast-sessions/{id}/recording
```

### B. 시간 기준점 보고 (방송 중 60초마다)

서버가 "녹화본의 몇 초 지점을 자를지" 계산하려면 **wall clock ↔ 녹화 경과시간**
매핑이 필요하다.

```ts
const { outputDuration } = await obs.call('GetRecordStatus')
await api.patch(`/broadcast-sessions/${id}/recording/base`, {
  baseWallClock: new Date().toISOString(),
  baseTimecodeMs: outputDuration
})
```

두 값은 **같은 순간에** 읽어야 한다. 사이가 벌어지면 그만큼이 그대로 클립 위치 오차가 된다.

60초마다 갱신하는 이유는 드리프트다. OBS 녹화가 프레임 드랍으로 실제 시각과 조금씩
어긋나서, 가장 최근 기준점을 써야 오차가 안 쌓인다.

### C. 클립 만들어 업로드 (`src/main/clip.ts` 신규)

```
GET /clip-jobs           → 할 일 목록 (보통 5개)
  ↓ 하나씩 순차로
ffmpeg 컷 + 썸네일        → 로컬 임시 파일
POST /highlights/{id}/clip (multipart)
  ↓ 임시 파일 삭제
```

**언제 호출하나**
- 앱 시작 직후 1회
- 리포트 완료 알림을 받았을 때

**pull인 이유** — 스트리머가 방송 끝나고 앱을 바로 끄는 건 정상이다.
서버가 밀어주는 방식이면 그 순간 작업이 사라지는데, 앱이 켜질 때마다 물어보면
며칠 뒤에 켜도 그대로 이어진다. 원본이 PC에 남아 있으니까.

---

## API

베이스: `/api/v1`. 인증은 **기존 사용자 JWT 그대로** — 다른 API와 똑같이 부르면 된다.

### 응답 봉투

이 프로젝트의 공통 형식을 그대로 따른다. **본문이 있는 응답은 전부 `data`로 한 겹 감싸여 있다.**

```jsonc
// 성공
{ "success": true, "data": { ... } }

// 실패
{ "success": false, "error": { "code": "CLIP_NOT_PENDING", "message": "..." } }
```

### 1. 녹화 시작

```
POST /api/v1/broadcast-sessions/{sessionId}/recording
```
```json
{
  "startedAt": "2026-07-31T20:00:03.120+09:00",
  "baseWallClock": "2026-07-31T20:00:03.120+09:00",
  "baseTimecodeMs": 0
}
```
→ `204 No Content`

같은 세션에 두 번 보내도 **에러가 아니다.** 기준점만 갱신하고 넘어간다 —
응답을 못 받고 재시도하는 것이 정상 흐름이라고 보고 그렇게 만들었다.

### 2. 기준점 갱신 (60초마다)

```
PATCH /api/v1/broadcast-sessions/{sessionId}/recording/base
```
```json
{ "baseWallClock": "2026-07-31T21:15:03.400+09:00", "baseTimecodeMs": 4500280 }
```
→ `204 No Content`

녹화가 이미 종료됐으면 조용히 무시된다(에러 아님). 종료 보고가 먼저 처리되고
마지막 갱신이 늦게 도착하는 순서 역전이 정상적으로 일어나기 때문이다.

### 3. 녹화 종료

```
PATCH /api/v1/broadcast-sessions/{sessionId}/recording
```
```json
{
  "endedAt": "2026-07-31T23:02:11.000+09:00",
  "outputPath": "C:\\Users\\str\\Videos\\2026-07-31 20-00-03.mkv",
  "status": "COMPLETED"
}
```
→ `204 No Content`

`status`는 `COMPLETED` 또는 `FAILED`. **`COMPLETED`를 보내야 그 세션의 클립 작업이 생긴다.**
녹화가 중간에 깨졌으면 `FAILED`를 보내라 — 그러면 작업이 아예 만들어지지 않는다.

`outputPath`는 `StopRecord` 응답 또는 `RecordStateChanged` 이벤트에서 얻는다.

### 4. 클립 작업 조회

```
GET /api/v1/clip-jobs
```
```json
{
  "success": true,
  "data": {
    "jobs": [
      {
        "highlightId": 4412,
        "sessionId": 8821,
        "sessionTitle": "7월 31일 저녁 방송",
        "recordingPath": "C:\\Users\\str\\Videos\\2026-07-31 20-00-03.mkv",
        "clipStartSec": 4495.5,
        "clipDurationSec": 80.0
      }
    ]
  }
}
```

**`clipStartSec`은 보정이 전부 끝난 값이다.** 송출 지연이니 반응 지연이니 계산하지 말고
ffmpeg에 그대로 넣으면 된다. 계산은 서버가 하고 단위 테스트로 고정해 뒀다.

작업이 없으면 `jobs`가 빈 배열이다. 녹화 보고가 없거나 아직 `COMPLETED`가 아닌
세션의 하이라이트는 목록에 안 나온다.

### 5. 클립 업로드

```
POST /api/v1/highlights/{highlightId}/clip
Content-Type: multipart/form-data
```

| 파트 이름 | 필수 타입 |
|---|---|
| `clip` | `video/mp4` |
| `thumbnail` | `image/jpeg` |

**파트 이름과 Content-Type을 정확히 맞춰야 한다.** 서버가 검사해서 안 맞으면
`CLIP_INVALID_FILE`로 거부한다.

```json
{
  "success": true,
  "data": {
    "clipUrl": "https://laive.example.com/clips/8821/4412.mp4",
    "thumbnailUrl": "https://laive.example.com/clips/8821/4412.jpg"
  }
}
```

**이미 올린 하이라이트에 다시 올려도 `200`에 기존 URL이 온다.** 저장은 됐는데
응답을 못 받고 앱이 꺼지는 경우를 정상 흐름으로 보고 그렇게 만들었으니,
재시도했다가 200이 오면 그냥 성공 처리하면 된다.

### 6. 실패 보고

```
POST /api/v1/highlights/{highlightId}/clip/failure
```
```json
{ "reason": "ffmpeg exited with code 1", "permanent": false }
```
→ `204 No Content`

| `permanent` | 언제 | 서버 동작 |
|---|---|---|
| `false` | ffmpeg 실패, 디스크 부족 등 다시 해볼 만한 실패 | 시도 횟수 +1, **3회**에 도달하면 포기 |
| `true` | 녹화 파일이 없어졌다 등 다시 해도 같은 실패 | 즉시 포기 |

`reason`은 200자까지만 저장된다(넘으면 잘림).

### 오류 코드

| code | HTTP | 뜻 |
|---|---|---|
| `RECORDING_SESSION_NOT_FOUND` | 404 | 없는 세션이거나 내 세션이 아님 |
| `RECORDING_NOT_FOUND` | 404 | 녹화 시작을 먼저 보고해야 함 |
| `HIGHLIGHT_NOT_FOUND` | 404 | 없는 하이라이트거나 내 것이 아님 |
| `CLIP_NOT_PENDING` | 409 | 이미 포기(FAILED/SKIPPED)된 작업 |
| `CLIP_INVALID_FILE` | 400 | 파트 누락, 빈 파일, Content-Type 불일치 |
| `CLIP_TOO_LARGE` | 413 | 200MB 초과 |
| `VALIDATION_ERROR` | 400 | 필수 필드 누락 등 |

---

## ffmpeg

`ffmpeg-static` 패키지를 electron-builder `extraResources`로 동봉한다.
스트리머 PC에 ffmpeg가 깔려 있다고 가정하면 안 된다. (앱 용량 +80MB)

**클립**
```bash
ffmpeg -ss {clipStartSec} -i "{recordingPath}" -t {clipDurationSec} \
  -c:v libx264 -preset veryfast -crf 23 -vf scale=-2:720 \
  -c:a aac -b:a 128k -movflags +faststart \
  clip.mp4
```

**썸네일** (구간 중앙)
```bash
ffmpeg -ss {clipStartSec + clipDurationSec/2} -i "{recordingPath}" \
  -frames:v 1 -vf scale=-2:360 -q:v 3 thumb.jpg
```

`-movflags +faststart` 빼지 말 것. 없으면 브라우저가 파일을 다 받아야 재생이 시작된다.

---

## 주의사항

**① 순차 처리.** 5개를 병렬로 인코딩하면 방송 직후 PC가 멎는다. 하나씩.

**② 실패하면 반드시 보고.** ffmpeg가 죽거나 파일이 없으면 `POST .../clip/failure`를 보낸다.
안 보내면 서버가 영원히 대기 상태로 들고 있다가 앱이 켜질 때마다 같은 작업을 다시 준다.

**③ 중복 업로드는 정상이다.** 위 5번 참고 — 200이 오면 성공으로 처리하면 된다.

**④ 스트리머 안내 2가지** (설정 화면 어딘가에 필요)
- OBS 녹화 설정 → **"스트림 인코더 재사용"** 켜기. 안 켜면 인코딩을 두 번 해서
  게임 프레임이 떨어진다.
- OBS 녹화 **자동 분할 끄기**. 파일이 쪼개지면 타임코드가 리셋돼서 엉뚱한 데를 자른다.

**⑤ 원본은 건드리지 않는다.** 4.5GB짜리 녹화본은 스트리머 자산이다.
지울지 말지는 나중에 UI로 물어보는 걸로 하고, 지금은 그대로 둔다.

---

## 체크리스트

- [ ] `obs.ts` — `StartRecord` / `StopRecord` / `GetRecordStatus` IPC 추가
- [ ] `preload/index.ts` + `useObs.ts`에 노출
- [ ] 녹화 보고 3개 API 연동 (A, B)
- [ ] `ffmpeg-static` 번들 + `extraResources` 설정
- [ ] **구간을 손으로 넣어 컷 테스트** ← 자동화보다 먼저
- [ ] `clip.ts` — 작업 조회 → 컷 → 업로드 → 임시파일 정리
- [ ] 실패 보고 경로 (`permanent` 구분 포함)
- [ ] 앱 껐다 켜도 이어지는지 확인
- [ ] 스트리머 안내 UI (④)

**컷 테스트를 먼저 해달라.** 파이프라인을 다 연결하고 나서 자른 위치가 틀린 걸
발견하면, 확인 한 번에 방송 한 번이 필요해진다.

---

## 확인해줄 것 두 가지

### 1. obs-websocket 응답 필드 (작업 A·B에서)

문서의 필드명은 추정이다. 실제 응답을 찍어보고 다르면 알려달라.

- `StopRecord` 응답의 `outputPath`
- `GetRecordStatus` 응답의 `outputDuration`

**이름이 다른 건 문제없다** — 서버가 받는 필드명은 우리가 정한 `outputPath`/`baseTimecodeMs`라
매핑만 하면 된다. 다만 **녹화 경과 시간을 주는 값이 아예 없으면** 시간 기준점을
다른 방법으로 잡아야 하고, 그러면 서버 계산도 같이 바뀐다.

### 2. `platform-delay` 캘리브레이션 (실방송 1회)

서버가 클립 위치를 계산할 때 CDN 송출 지연 **4.5초**를 빼고 있다. 미검증 초기값이라
실제로 맞는지는 실방송을 돌려봐야 안다.

첫 클립이 나오면 **"채팅이 터진 장면이 클립 안에 제대로 들어왔는지"** 눈으로 봐주고,
- 사건이 클립 **앞쪽에서 잘려 있으면** → 더 빼야 함 (값을 키운다)
- 사건이 클립 **뒤쪽에 몰려 있으면** → 덜 빼야 함 (값을 줄인다)

알려주면 서버 설정만 바꾸면 된다. 앱은 수정할 게 없다.

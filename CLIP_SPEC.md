# 하이라이트 클립 생성 명세

> 대상: **Electron 클라이언트**와 **Spring**
> 하이라이트 구간을 확정하는 판정 알고리즘은 [HIGHLIGHT_SPEC.md](AI/chat-ai/HIGHLIGHT_SPEC.md),
> 구간 선별(상위 5개) 로직은 이미 `DeterministicHighlightGenerator`에 구현되어 있다.
> 이 문서는 **확정된 구간의 영상 파일을 만들어 `highlights.clip_url`을 채우는 방법**만 다룬다.

방송 종료 후, 리포트가 선별한 하이라이트 구간의 영상을 클립으로 만들어 저장한다.
영상은 **스트리머 PC의 OBS 로컬 녹화본**에서 잘라내며, 서버는 완성된 클립만 받는다.

---

## 0. 왜 서버가 영상을 녹화하지 않나

먼저 검토했던 방식은 OBS 멀티 송출로 서버가 RTMP를 받아 직접 녹화하는 것이었다.
**채택하지 않는다.** Electron 클라이언트가 이미 스트리머 PC에서 돌고 있고,
`src/main/obs.ts`에서 obs-websocket으로 OBS를 제어하고 있기 때문이다.
영상을 서버로 가져올 방법이 없다는 전제가 성립하지 않는다.

| | 서버 RTMP 녹화 | **OBS 로컬 녹화 (채택)** |
|---|---|---|
| 서버 디스크 | 방송당 4~11GB (임시) | **방송당 ~100MB** |
| 서버 CPU | 인코딩 부담 큼 | **0** |
| 서버 대역폭 | 3시간 내내 스트림 수신 | **방송 후 100MB 한 번** |
| 인프라 | RTMP 포트·포트풀·보안그룹 | **추가 없음** |
| 화질 | RTMP 경유 재인코딩 | **원본** |
| 장애 복구 | 서버 디스크에서만 가능 | 원본이 PC에 남아 언제든 재시도 |

특히 **서버 디스크가 결정적이다.** EC2 한 대에 전 컴포넌트를 올리는 구성에서
방송 3~4번이면 볼륨이 찬다. 원본을 스트리머 PC에 두면 서버가 만지는 것은
완성된 클립 100MB뿐이다.

**AI 서버는 이 파이프라인에 관여하지 않는다.** `AI/app`은 브라우저 마이크 PCM을
실시간 지연 예산(사전매칭 0.4초·LLM 2.2초) 안에서 처리한다. 여기에 인코딩을
얹으면 위험 발언 경고가 느려진다. 클립은 늦어도 되지만 경고는 늦으면 기능이
무의미해진다.

## 1. 왜 트리거 발동 시점이 아니라 방송 종료 후인가

두 가지 이유가 있고, 두 번째가 결정적이다.

1. **낭비** — 트리거는 방송당 20~30개 확정되는데 하이라이트는 최대 5개다
   (`report.ai.max-highlights`). 트리거마다 올리면 80%를 버린다.
2. **방송 중에는 순위를 알 수 없다** — `DeterministicHighlightGenerator`의 점수
   계산에 `maximumUnique`(그 방송 전체 트리거 중 최대 유니크 채터 수)가 들어간다.
   방송이 끝나기 전에는 어떤 구간이 3위인지 15위인지 확정되지 않는다.

녹화 원본이 스트리머 PC에 통째로 남아 있으므로 서두를 이유가 없다.
5개가 확정된 뒤에 그 5개만 자른다.

## 2. 전체 흐름

```
[방송 시작]
  Electron  obs.call('StartStream')          ← 이미 구현됨
          + obs.call('StartRecord')          ← 추가
          + GetRecordStatus로 타임코드 기준점 확보
          → POST /broadcast-sessions/{id}/recording

[방송 중]
  서버는 영상에 대해 아무것도 하지 않는다. 채팅 수집·트리거 판정만 (기존 그대로)
  Electron은 주기적으로 타임코드 기준점을 갱신 (§4)

[방송 종료]
  Electron  obs.call('StopRecord') → 최종 outputPath 확보
          → PATCH /broadcast-sessions/{id}/recording

  Spring    리포트 생성 → 하이라이트 최대 5개 확정 (기존 로직 그대로)
          → highlights INSERT (clip_url = NULL, clip_status = PENDING)

[클립 생성]                                   ← 이 문서가 정의하는 구간
  Electron  GET /clip-jobs           작업 목록 조회 (pull)
          → 로컬 ffmpeg로 컷 + 썸네일
          → POST /highlights/{id}/clip       (multipart 업로드)

  Spring    파일 저장 → UPDATE highlights SET clip_url, thumbnail_url,
                                            clip_status = 'UPLOADED'
```

**작업 전달이 push가 아니라 pull인 이유** — 스트리머가 방송 종료 직후 앱을 끄는
것은 정상 행동이다. push로 밀면 그 순간 작업이 증발한다. 앱이 켜질 때마다
미완료 작업을 물어보면 다음 실행에서 자동으로 이어진다. 원본이 PC에 남아 있는 한
며칠 뒤에 켜도 복구된다.

## 3. 저장 위치와 용량

| 무엇 | 어디 | 크기 | 수명 |
|---|---|---|---|
| 녹화 원본 | **스트리머 PC** — OBS 녹화 폴더 | 4.5GB (720p 2.5Mbps 4시간) | 스트리머 자산. 삭제는 스트리머 판단 |
| 클립 mp4 × 5 | **EC2** `/var/laive/clips/{sessionId}/` | ~100MB / 방송 | 영구 |
| 썸네일 jpg × 5 | 위와 같은 폴더 | 무시 가능 | 영구 |

**영상 파일은 DB에 들어가지 않는다.** `clip_url`·`thumbnail_url`은 `VARCHAR(1024)`
문자열이고 실제 파일은 디스크에 있다. `<video>`는 Range 요청으로 필요한 부분만
받아가는데 DB BLOB은 전량 로드해야 첫 프레임이 뜨고, 20MB 읽기가 HikariCP
커넥션(`maximum-pool-size: 10`)을 수 초씩 잡는다.

**DB에는 상대 경로를 저장하고 절대 URL은 조회 시 조립한다.**

| 단계 | 값 |
|---|---|
| 디스크 | `/var/laive/clips/8821/4412.mp4` |
| DB `clip_url` | `8821/4412.mp4` ← 키만 |
| API 응답 `clipUrl` | `https://laive.example.com/clips/8821/4412.mp4` |

절대 URL을 저장하면 도메인이 바뀔 때마다 DB를 UPDATE해야 한다. EC2 IP로 쓰다가
발표용 도메인을 붙이는 순간 그전 클립이 전부 죽은 링크가 된다.

`/clips` 접두사까지 빼고 키만 저장하는 이유는 S3 이전이다 — 이 키가 그대로
오브젝트 키가 되므로 `laive.clip.public-base-url`만 바꾸면 된다.

`clip_url`은 nginx 정적 서빙 경로다. **오브젝트 스토리지를 두지 않는다** —
방송 100번에 10GB라 S3·MinIO 계층은 순수 오버헤드다.

```nginx
location /clips/ {
    alias /var/laive/clips/;
    add_header Cache-Control "public, max-age=31536000, immutable";
}
```

다만 Spring 쪽은 `ClipStorage` 인터페이스 뒤에 둔다. 나중에 S3로 옮길 때
구현체만 교체하면 되고, 이는 `ChatCollectorGateway`가 HTTP 구현을 숨기는 것과
같은 구조다.

```java
public interface ClipStorage {
    /** 저장하고 공개 URL을 돌려준다. */
    String store(long sessionId, long highlightId, ClipUpload upload);
}
```

**스트리머 PC 부담을 줄이는 안내** — OBS 녹화 설정에서 "스트림 인코더 재사용"을
켜면 추가 인코딩이 발생하지 않는다. 이 설정 없이 x264 CPU 인코딩을 두 번 돌리면
게임 프레임이 떨어진다.

## 4. 시간축 정합 — 가장 틀리기 쉬운 부분

**채팅 시각과 녹화 타임코드는 같은 축이 아니다.** 두 단계 보정이 필요하다.

### 4.1 CDN 송출 지연

시청자는 CDN을 거친 화면을 보고, 채팅은 **그 지연된 화면**에 대한 반응이다.
OBS 로컬 녹화는 CDN을 거치지 않으므로 채팅보다 4~5초 빠르다.

```
클립 시작 = (트리거 시작 시각 기준 녹화 타임코드) - platform_delay
```

`platform_delay` 기본값 4500ms. 실방송 1회로 눈으로 맞춰 보정한다.
**이 값을 빼먹으면 클립이 전부 "터지기 직전"에서 끝난다.**

이 4~5초는 팀이 이미 알고 있는 값이다 — `application.yaml`의
`repeated-questions.dismiss-grace` 주석에 "송출 지연 4~5초"로 명시되어 있다.

### 4.2 인간 반응 지연 — 이미 처리되어 있다

사람은 재밌는 장면을 본 **다음에** 채팅을 친다. 반응 시점부터 자르면 원인 장면이
빠진다. 이는 `highlight.lead: 30s`로 트리거 확정 시 시작점을 30초 소급해
이미 해결되어 있다(`TriggerRecorder`). **클립 단계에서 추가 보정하지 말 것.**
두 번 빼면 30초 앞이 통째로 들어간다.

### 4.3 wall clock ↔ 타임코드 매핑

녹화 시작 시각을 추정하면 안 된다. obs-websocket의 `GetRecordStatus`가
`outputDuration`(녹화 경과 ms)을 직접 주므로, 이를 읽은 wall clock과 짝지어
기준점으로 저장한다.

```
Electron이 60초마다:
    duration = (await obs.call('GetRecordStatus')).outputDuration
    → POST 기준점 { wallClock: now(), timecodeMs: duration }

Spring이 클립 구간 계산:
    clipStartMs = (trigger.startedAt - base.wallClock) + base.timecodeMs - platformDelay
```

**계산 주체는 Spring이다.** Electron에는 완성된 `clipStartSec`/`clipDurationSec`만
내려준다. 보정값이 서버 설정이고, 클라이언트 버전마다 계산이 갈리면 원인 추적이
불가능해진다.

기준점을 주기적으로 갱신하는 이유는 드리프트다. OBS 녹화가 프레임 드랍이나
인코더 지연으로 wall clock과 서서히 어긋난다. 가장 가까운 기준점을 쓰면
누적 오차가 사라진다.

## 5. 데이터 모델 (V12)

`highlights`는 그대로 쓴다. `clip_url`·`thumbnail_url`이 이미 nullable로 있고
V1 주석이 "클립 생성 전·실패 시 NULL"을 이미 계약으로 선언해 두었다.
여기에 작업 상태만 덧붙인다.

```sql
-- =============================================================================
-- V12: 하이라이트 클립 생성 (CLIP_SPEC.md)
--
-- 별도 clip_jobs 테이블을 두지 않는 이유
--   작업 대상은 "clip_status = PENDING인 highlights" 그 자체다. 테이블을 나누면
--   highlights와 1:1인 행을 두 곳에서 동기화해야 하고, 리포트 재생성으로
--   highlights가 교체될 때 고아 작업이 남는다.
-- =============================================================================

CREATE TABLE recordings (
    session_id       BIGINT      PRIMARY KEY REFERENCES broadcast_sessions (id) ON DELETE CASCADE,

    -- OBS가 알려주는 최종 파일 경로. 스트리머 PC 기준 절대 경로이고
    -- 서버는 이 값을 해석하지 않는다 — Electron에 그대로 돌려줄 뿐이다.
    output_path      TEXT,

    -- wall clock ↔ 녹화 타임코드 매핑 기준점 (§4.3). 드리프트 때문에
    -- 주기적으로 갱신되며, 항상 마지막 값만 유지한다.
    base_wall_clock  TIMESTAMPTZ,
    base_timecode_ms BIGINT,

    status           VARCHAR(20) NOT NULL DEFAULT 'RECORDING',
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_recordings_status
        CHECK (status IN ('RECORDING', 'COMPLETED', 'FAILED')),
    -- 기준점은 두 컬럼이 함께 있거나 함께 없다
    CONSTRAINT ck_recordings_base
        CHECK ((base_wall_clock IS NULL) = (base_timecode_ms IS NULL))
);

CREATE TRIGGER trg_recordings_updated_at
    BEFORE UPDATE ON recordings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE recordings IS 'OBS 로컬 녹화본 위치와 시간축 기준점 (CLIP_SPEC.md)';


ALTER TABLE highlights
    -- PENDING  아직 클립 없음 (기본)
    -- UPLOADED 클립 저장 완료
    -- FAILED   max-attempts 소진. 더 시도하지 않는다
    -- SKIPPED  녹화본이 없다 (녹화 미실행·파일 삭제). 재시도 대상이 아니다
    ADD COLUMN clip_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN clip_attempts  INT          NOT NULL DEFAULT 0,
    ADD COLUMN clip_failed_reason VARCHAR(200),

    ADD CONSTRAINT ck_highlights_clip_status
        CHECK (clip_status IN ('PENDING', 'UPLOADED', 'FAILED', 'SKIPPED')),
    -- URL이 있다는 것은 업로드가 끝났다는 뜻이다
    ADD CONSTRAINT ck_highlights_clip_url_status
        CHECK (clip_url IS NULL OR clip_status = 'UPLOADED');

-- 앱이 켜질 때 미완료 작업을 훑는 경로
CREATE INDEX ix_highlights_clip_pending ON highlights (clip_status)
    WHERE clip_status = 'PENDING';
```

**Outbox 이벤트를 추가하지 않는다.** Outbox는 서버가 스스로 해야 할 일을 재시도하는
장치인데, 여기서 일하는 주체는 Electron이다. 서버가 재시도할 수 있는 것이 없다.
재시도 책임은 `clip_attempts`와 앱의 pull 주기가 진다.

## 6. API 계약

인증은 기존 사용자 JWT를 그대로 쓴다. 내부 서비스 토큰(`/internal/**`)이 아니다 —
호출자가 스트리머 본인의 앱이기 때문이다.

### 6.1 녹화 시작 보고

```
POST /api/v1/broadcast-sessions/{sessionId}/recording
{
  "startedAt":     "2026-07-31T20:00:03.120+09:00",
  "baseWallClock": "2026-07-31T20:00:03.120+09:00",
  "baseTimecodeMs": 0
}
→ 204
```

### 6.2 기준점 갱신 (60초 주기)

```
PATCH /api/v1/broadcast-sessions/{sessionId}/recording/base
{ "baseWallClock": "2026-07-31T21:15:03.400+09:00", "baseTimecodeMs": 4500280 }
→ 204
```

### 6.3 녹화 종료 보고

```
PATCH /api/v1/broadcast-sessions/{sessionId}/recording
{ "endedAt": "2026-07-31T23:02:11.000+09:00",
  "outputPath": "C:\\Users\\str\\Videos\\2026-07-31 20-00-03.mkv",
  "status": "COMPLETED" }
→ 204
```

`outputPath`는 `StopRecord` 응답 또는 `RecordStateChanged` 이벤트에서 얻는다.

### 6.4 클립 작업 조회 (pull)

```
GET /api/v1/clip-jobs
→ 200
{
  "jobs": [
    {
      "highlightId": 4412,
      "sessionId": 8821,
      "sessionTitle": "7월 31일 저녁 방송",
      "recordingPath": "C:\\Users\\str\\Videos\\2026-07-31 20-00-03.mkv",
      "clipStartSec": 4495.78,
      "clipDurationSec": 80.0
    }
  ]
}
```

로그인한 스트리머의 `clip_status = 'PENDING'`이고 `recordings.status = 'COMPLETED'`인
하이라이트만 내려준다. **`clipStartSec`은 §4의 보정이 모두 끝난 값이다** —
Electron은 이 숫자를 ffmpeg에 그대로 넣기만 한다.

### 6.5 클립 업로드

```
POST /api/v1/highlights/{highlightId}/clip
Content-Type: multipart/form-data
  clip:      (mp4)
  thumbnail: (jpg)
→ 200 { "clipUrl": "...", "thumbnailUrl": "..." }
```

업로드 크기 상한을 둔다(기본 200MB). 이미 `UPLOADED`인 하이라이트에 다시 오면
409가 아니라 **200에 기존 URL을 돌려준다** — 업로드 응답을 못 받고 앱이 재시도하는
경우가 정상 흐름이라, 여기서 에러를 내면 앱이 영원히 재시도한다.

### 6.6 실패 보고

```
POST /api/v1/highlights/{highlightId}/clip/failure
{ "reason": "recording file not found" }
→ 204
```

`clip_attempts`를 올리고, `max-attempts`에 도달하면 `FAILED`로 확정한다.
파일이 없는 경우는 재시도해도 결과가 같으므로 즉시 `SKIPPED`로 보낸다.

## 7. ffmpeg 명령

Electron main이 실행한다. `ffmpeg-static`을 electron-builder `extraResources`로
동봉한다. 스트리머 PC에 ffmpeg가 설치되어 있으리라 기대하면 안 된다.

**클립** — 재인코딩한다. `-c copy`는 키프레임 단위로만 잘려 최대 2초가 어긋나는데,
1분짜리 5개면 `veryfast`로 수십 초면 끝난다.

```bash
ffmpeg -ss 4495.78 -i "record.mkv" -t 80 \
  -c:v libx264 -preset veryfast -crf 23 -vf scale=-2:720 \
  -c:a aac -b:a 128k -movflags +faststart \
  clip.mp4
```

`-movflags +faststart`는 필수다. 없으면 색인이 파일 끝에 있어 브라우저가
전체를 받은 뒤에야 재생을 시작한다.

**썸네일** — 구간 중앙 프레임.

```bash
ffmpeg -ss 4535.78 -i "record.mkv" -frames:v 1 -vf scale=-2:360 -q:v 3 thumb.jpg
```

## 8. 실패와 복구

| 상황 | 처리 |
|---|---|
| 방송 후 앱을 바로 껐다 | 다음 실행 시 `GET /clip-jobs`가 그대로 내려준다. 원본이 PC에 있는 한 며칠 뒤도 가능 |
| 스트리머가 원본을 지웠다 | `SKIPPED`. 리포트는 클립 없이 정상 표시 |
| OBS 녹화를 안 켰다 | `recordings` 행이 없다 → 작업이 생성되지 않는다 |
| ffmpeg 실패 | `clip_attempts` 증가, 3회 후 `FAILED` |
| 업로드 중 네트워크 끊김 | `PENDING` 유지 → 다음 pull에서 재시도. 중복 업로드는 §6.5가 흡수 |
| 리포트 재생성 | `highlights`가 교체되며 새 행은 `PENDING`. 이전 클립 파일은 고아가 된다 (§10) |

**클립이 없어도 리포트는 완전하다.** `clip_url IS NULL`은 정상 상태이고,
프론트는 썸네일 자리에 대체 표시를 한다. 클립 실패가 리포트 상태를
`PARTIAL`로 만들지 않는다.

## 9. 설정값

```yaml
laive:
  clip:
    # CDN 송출 지연 보정 (§4.1). 실방송 1회로 캘리브레이션한다.
    platform-delay: 4500ms
    # 실패 재시도 상한. 소진하면 FAILED로 확정하고 앱이 더 이상 시도하지 않는다.
    max-attempts: 3
    storage-dir: "${CLIP_STORAGE_DIR:/var/laive/clips}"
    # clip_url에 들어갈 접두사. nginx가 storage-dir을 이 경로로 서빙한다.
    public-base-url: "${CLIP_PUBLIC_BASE_URL:http://localhost:8080/clips}"
    max-upload-bytes: 209715200   # 200MB
```

Electron 쪽:

| 항목 | 값 |
|---|---|
| 기준점 갱신 주기 | 60초 |
| 작업 pull 주기 | 앱 시작 시 1회 + 리포트 완료 알림 수신 시 |
| 동시 처리 | **순차 1개** — 5개를 병렬 인코딩하면 방송 직후 PC가 멎는다 |

## 10. 미결 사항

- **원본 삭제 안내** — 클립 추출 후 4.5GB 원본을 지울지 스트리머에게 물을 것인가.
  기본은 건드리지 않는다(스트리머 자산). UI 결정 필요.
- **OBS 녹화 자동 분할** — OBS 설정에서 파일 자동 분할을 켜면 원본이 여러 개로
  쪼개져 타임코드가 파일마다 리셋된다. MVP는 "분할 끄기" 안내로 회피하고,
  대응이 필요해지면 `recordings`를 1:N으로 확장한다.
- **고아 클립 정리** — 리포트 재생성 시 이전 클립 파일이 디스크에 남는다.
  방송 100번에 10GB 규모라 급하지 않지만, 주기적 정리 배치가 언젠가 필요하다.
- **외부 명세 반영** — 이 문서의 API를 팀 명세 문서에 절 번호를 받아 편입해야 한다.

## 11. 구현 순서

1. **Electron** — `obs.ts`에 `StartRecord`/`StopRecord`/`GetRecordStatus` IPC 추가.
   녹화가 실제로 되는지부터 확인한다.
2. **Spring** — V12 마이그레이션 + 녹화 보고 API (§6.1~6.3).
3. **Electron** — ffmpeg 번들 + **구간을 손으로 넣어 컷 테스트**.
   여기서 `platform-delay`를 눈으로 맞춘다.
4. **Spring** — `GET /clip-jobs` + 업로드 수신 + `ClipStorage` + nginx 서빙.
5. 연결 — 리포트 완료 후 앱이 자동으로 작업을 가져와 처리.
6. 앱을 껐다 켜도 복구되는지 확인.

**3번을 자동화보다 먼저 한다.** 파이프라인을 다 연결한 뒤에 오프셋이 틀린 것을
발견하면 디버깅 한 바퀴가 방송 한 번이 된다.

# 하이라이트 클립 — Spring 작업 명세

> 전체 설계는 [../../../CLIP_SPEC.md](../../../CLIP_SPEC.md),
> 프론트(Electron) 몫은 [../../../FrontEnd/CLIP_SPEC.md](../../../FrontEnd/CLIP_SPEC.md).
> 이 문서는 **Spring이 할 일만** 추렸다.

영상은 스트리머 PC의 OBS 로컬 녹화본에서 Electron이 잘라 올린다.
**Spring은 ffmpeg를 실행하지 않고 영상을 녹화하지도 않는다.** 하는 일은 셋이다.

1. 녹화본 위치와 시간 기준점을 기억한다
2. **어디를 잘라야 하는지 계산해서 알려준다** ← 여기가 핵심
3. 올라온 클립을 저장하고 `highlights.clip_url`을 채운다

**기존 리포트 파이프라인은 손대지 않는다.** `ReportCompletionService`가 이미
`highlights`를 저장하고, 새 컬럼은 전부 DEFAULT가 있어 그대로 `PENDING`이 된다.

---

## 작업 4개

### A. V12 마이그레이션

`recordings` 신규 + `highlights` 컬럼 추가. 전문은 [전체 설계 §5](../../../CLIP_SPEC.md) 참고.

```sql
CREATE TABLE recordings (
    session_id       BIGINT      PRIMARY KEY REFERENCES broadcast_sessions (id) ON DELETE CASCADE,
    output_path      TEXT,                    -- 스트리머 PC 절대 경로. 서버는 해석하지 않는다
    base_wall_clock  TIMESTAMPTZ,             -- 시간 기준점 (§오프셋 계산)
    base_timecode_ms BIGINT,
    status           VARCHAR(20) NOT NULL DEFAULT 'RECORDING',
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    ...
);

ALTER TABLE highlights
    ADD COLUMN clip_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN clip_attempts      INT         NOT NULL DEFAULT 0,
    ADD COLUMN clip_failed_reason VARCHAR(200);
```

`clip_status`: `PENDING` / `UPLOADED` / `FAILED` / `SKIPPED`

**별도 `clip_jobs` 테이블을 두지 않는다.** 작업 대상은 `clip_status = 'PENDING'`인
`highlights` 그 자체다. 나누면 1:1 행을 두 곳에서 동기화해야 하고, 리포트가
재생성돼 `highlights`가 교체될 때 고아 작업이 남는다.

### B. 녹화 보고 수신 (3개)

Electron이 방송 시작·중·종료에 보고한다. 저장만 하면 된다.

```
POST  /api/v1/broadcast-sessions/{id}/recording        시작   → recordings INSERT
PATCH /api/v1/broadcast-sessions/{id}/recording/base   60초마다 → 기준점 UPDATE
PATCH /api/v1/broadcast-sessions/{id}/recording        종료   → output_path, status='COMPLETED'
```

### C. 클립 작업 조회 — **오프셋 계산**

```
GET /api/v1/clip-jobs
```

로그인한 스트리머의 하이라이트 중 아래를 모두 만족하는 것만 내려준다.

- `highlights.clip_status = 'PENDING'`
- `recordings.status = 'COMPLETED'` 이고 `output_path`가 있다
- `base_wall_clock` / `base_timecode_ms`가 있다

**계산식**

```
clipStartMs    = (trigger.started_at - base_wall_clock) + base_timecode_ms - platform_delay
clipDurationMs = highlight.end_ms - highlight.start_ms
```

**절대 시각은 `triggers.started_at`을 그대로 쓴다.** `highlights.trigger_id`가
`triggers`를 참조하므로 조인 한 번이면 된다. `highlights.start_ms`(세션 시작
기준 오프셋)에 `broadcast_sessions.started_at`을 더해 재계산하지 말 것 —
`triggers`가 오프셋과 절대 시각을 둘 다 저장한 이유가 정확히 이것이다.

> 조회할 때마다 세션 시작 시각과 조인해 변환하면 오프셋과 절대 시각을 섞는
> 실수가 조인 지점마다 되살아난다. — `V1__baseline_schema.sql` (triggers 절)

`trigger_id`는 `ON DELETE SET NULL`이라 NULL일 수 있다. NULL이면
`session.started_at + highlight.start_ms`로 폴백하되, 이 경로를 타는 하이라이트는
로그를 남긴다 — 정상 흐름에서는 발생하지 않는다.

**`platform_delay`(기본 4500ms)를 여기서 뺀다.** 시청자는 CDN을 거친 화면을 보고
채팅은 그 지연된 화면에 대한 반응인데, OBS 로컬 녹화는 CDN을 안 거쳐 4~5초 빠르다.
안 빼면 클립이 전부 "터지기 직전"에서 끝난다.

**반응 지연은 이미 처리되어 있다.** `highlight.lead: 30s`로 트리거 확정 시
시작점을 30초 소급했다(`TriggerRecorder`). **여기서 또 빼지 말 것.**
두 번 빼면 30초 앞이 통째로 들어온다.

`clipStartMs`가 음수면 0으로 클램프한다 — 녹화가 방송보다 늦게 시작된 경우다.

**계산을 서버가 하는 이유** — 보정값이 서버 설정이다. 클라이언트가 계산하면
앱 버전마다 결과가 갈려서 "클립이 이상하다"는 제보의 원인 추적이 불가능해진다.
Electron에는 완성된 `clipStartSec` / `clipDurationSec`만 내려준다.

### D. 업로드 수신 + 저장

```
POST /api/v1/highlights/{id}/clip          multipart: clip(mp4), thumbnail(jpg)
POST /api/v1/highlights/{id}/clip/failure  { reason }
```

`ClipStorage`에 저장하고 `clip_url` / `thumbnail_url` / `clip_status='UPLOADED'` UPDATE.

```java
public interface ClipStorage {
    /** 저장하고 DB에 넣을 상대 경로를 돌려준다. */
    String store(long sessionId, long highlightId, ClipUpload upload);
}
```

**DB에는 상대 경로를 저장하고, 절대 URL은 조회할 때 조립한다.**

| 단계 | 값 |
|---|---|
| 디스크 | `/var/laive/clips/8821/4412.mp4` |
| DB `clip_url` | `8821/4412.mp4` ← 키만. `/clips` 접두사도 넣지 않는다 |
| API 응답 `clipUrl` | `https://laive.example.com/clips/8821/4412.mp4` |

절대 URL을 통째로 저장하면 **도메인이 바뀔 때마다 DB를 UPDATE해야 한다.**
EC2 IP로 쓰다가 발표용 도메인을 붙이는 순간 그전에 만든 클립이 전부 죽은 링크가
되고, 로컬 개발 DB에 `localhost:8080`이 박히는 것도 같은 문제다.

조립 지점은 `HighlightResponse.from()`이다 — 이미 도메인 객체를 DTO로 옮기는
자리라 `public-base-url`을 붙일 곳이 따로 필요 없다. 나중에 S3로 옮길 때도
설정값 하나만 바꾸면 된다.

**프론트가 받는 것은 완성된 절대 URL이다.** 조립은 서버 몫이고 프론트 계약은
바뀌지 않는다.

파일명은 `{highlightId}`를 쓴다. 리포트를 재생성하면 새 `highlightId`가 발급되므로
파일이 충돌하지 않는다. 대신 이전 파일이 고아로 남는다(전체 설계 §10).

구현체는 `LocalDiskClipStorage` 하나. **S3·MinIO를 붙이지 않는다** —
방송 100회에 10GB라 오브젝트 스토리지 계층은 순수 오버헤드다. 인터페이스만
유지하면 나중에 교체가 구현체 하나 추가로 끝난다.

---

## 패키지 배치

```
com.example.laive.media
├─ config/        ClipProperties
├─ domain/        Recording, RecordingStatus, ClipStatus, ClipJob
├─ controller/    RecordingController, ClipJobController, ClipUploadController
├─ service/       RecordingService, ClipJobService, ClipUploadService
├─ storage/       ClipStorage, LocalDiskClipStorage
└─ repository/mybatis/  RecordingMapper, ClipMapper
```

MyBatis XML은 `src/main/resources/mapper/media/`.

---

## 주의사항

**① 소유권 검사를 빠뜨리지 말 것.** 전 API가 사용자 JWT 인증이다
(`/internal/**`의 서비스 토큰이 아니다 — 호출자가 스트리머 본인의 앱이다).
남의 세션에 녹화를 보고하거나 남의 하이라이트에 클립을 올릴 수 없어야 한다.
`highlights → reports → broadcast_sessions.streamer_id`로 확인한다.

**② 중복 업로드는 409가 아니라 200이다.** 이미 `UPLOADED`인 하이라이트에 다시
오면 기존 URL을 그대로 돌려준다. 업로드는 성공했는데 응답을 못 받고 앱이 꺼지는
것이 정상 흐름이라, 여기서 에러를 내면 앱이 영원히 재시도한다.

**③ 업로드 크기·타입 검증.** `spring.servlet.multipart` 설정을 함께 올려야 한다.
기본값(1MB)이면 전부 거부된다.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 220MB
```

**④ 클립 실패가 리포트를 `PARTIAL`로 만들지 않는다.** `clip_url IS NULL`은
정상 상태다 — V1 스키마 주석이 이미 "클립 생성 전·실패 시 NULL"을 계약으로
선언해 두었다. `ReportCompletionService`의 `partial` 판정에 클립을 넣지 말 것.

**⑤ Outbox를 쓰지 않는다.** Outbox는 서버가 스스로 할 일을 재시도하는 장치인데,
여기서 일하는 주체는 Electron이다. 서버가 재시도할 수 있는 것이 없다.
재시도 책임은 `clip_attempts`와 앱의 pull 주기가 진다.

**⑥ 녹화 보고가 없는 세션은 그냥 `PENDING`으로 둔다.** `GET /clip-jobs`가
`recordings.status = 'COMPLETED'` 조건으로 거르므로 목록에 안 나온다.
억지로 `SKIPPED`로 마킹하지 않는 편이 낫다 — 녹화 종료 보고가 늦게 들어오는
경우에 자동으로 되살아난다.

---

## 설정

```yaml
laive:
  clip:
    # CDN 송출 지연 보정. 실방송 1회로 캘리브레이션한다.
    platform-delay: 4500ms
    # 실패 재시도 상한. 소진하면 FAILED로 확정한다.
    max-attempts: 3
    storage-dir: "${CLIP_STORAGE_DIR:/var/laive/clips}"
    # clip_url 접두사. nginx가 storage-dir을 이 경로로 서빙한다.
    public-base-url: "${CLIP_PUBLIC_BASE_URL:http://localhost:8080/clips}"
    max-upload-bytes: 209715200   # 200MB
```

## 인프라 (EC2)

```nginx
# 업로드 경로 — 기본값이 1MB라 이걸 안 올리면 클립 업로드가 413으로 전부 막힌다.
# Spring의 spring.servlet.multipart 설정만 올리고 여기를 빠뜨리는 것이
# 이 기능에서 가장 흔한 사고다. 요청이 Spring에 도달조차 하지 않는다.
client_max_body_size 220M;

location /clips/ {
    alias /var/laive/clips/;
    add_header Cache-Control "public, max-age=31536000, immutable";
}
```

```
/var/laive/clips/{sessionId}/{highlightId}.mp4
/var/laive/clips/{sessionId}/{highlightId}.jpg
```

**디렉터리 권한** — Spring 프로세스가 쓰고 nginx가 읽는다. 두 실행 계정이 다르면
업로드는 성공하는데 재생만 403이 된다. 디렉터리는 `755`, 파일은 `644`로 만든다.
Spring을 도커로 띄운다면 컨테이너와 호스트가 같은 경로를 보도록
볼륨을 마운트하고 nginx도 그 호스트 경로를 서빙해야 한다.

**HTTPS가 필요하다** — Electron이 클립을 업로드하고, 리포트 화면이 `<video>`로
클립을 불러온다. API가 HTTPS인데 클립이 HTTP면 mixed content로 재생이 차단된다.

**인스턴스를 재생성하면 클립이 전부 사라진다.** 발표 전에 인스턴스를 갈아엎을
계획이 있으면 이 디렉터리를 별도 EBS 볼륨으로 빼거나 백업을 떠둔다.
용량은 방송 1회에 ~100MB다.

**추가로 열어야 할 포트는 없다.** 서버가 RTMP를 받지 않는 설계라(전체 설계 §0)
보안 그룹은 기존 그대로다.

---

## 체크리스트

- [x] `recordings` 테이블 + `highlights.clip_*` (`V1__baseline_schema.sql`)
- [x] `RecordingController` — 보고 3개 + 소유권 검사
- [x] `ClipJobService` — **오프셋 계산** + `GET /clip-jobs`
- [x] `ClipStorage` + `LocalDiskClipStorage`
- [x] 업로드 수신 + `clip_url` UPDATE (중복 업로드 200 처리 포함)
- [x] 실패 보고 → `clip_attempts` 증가 → `max-attempts`에서 `FAILED`
- [x] `HighlightResponse`에 `public-base-url` 조립
- [x] `spring.servlet.multipart` 설정
- [x] 오프셋 계산 단위 테스트 8종 — `platform_delay` 적용, 드리프트, 앞 잘림 클램프,
      전체 이탈, 길이 0 거부, `trigger_id IS NULL` 폴백, 음수 타임코드 거부
- [x] 파이프라인 통합 테스트 12종 — 조인·상태 전이·소유권·멱등 업로드
- [ ] nginx `/clips/` 서빙 (인프라)

**아직 실방송 검증이 남았다.** `platform-delay: 4500ms`는 미검증 초기값이고,
이 값이 틀리면 클립이 엉뚱한 데서 잘린다. 테스트는 계산이 설정대로 도는 것까지만
보장하며, 설정값 자체가 맞는지는 실방송 1회로만 확인된다.

---

## 프론트와 맞춰야 할 것

**Spring 쪽 계약은 이미 확정이고 프론트를 기다릴 필요가 없다.** 요청 본문의
`baseWallClock` / `baseTimecodeMs` / `outputPath`는 우리가 정한 이름이고,
obs-websocket 응답을 여기에 맞춰 넣는 것은 Electron 몫이다.

다만 Electron이 작업 1번에서 확인하기로 한 것이 하나 있다 — obs-websocket v5가
**녹화 경과 시간을 실제로 제공하는가**(`GetRecordStatus`의 `outputDuration` 추정).
제공하지 않는다면 시간 기준점을 다른 방법으로 잡아야 하고, 그때는 §오프셋 계산이
통째로 바뀐다. 필드 *이름*이 다른 것은 문제가 아니지만 값 자체가 없으면 문제다.

확률은 낮으므로 백엔드는 기다리지 말고 진행한다.

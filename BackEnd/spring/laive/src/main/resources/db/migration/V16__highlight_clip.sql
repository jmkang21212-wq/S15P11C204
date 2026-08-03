-- =============================================================================
-- LAIVE - 1인 스트리머 AI 매니저
-- V16: 하이라이트 클립 생성 (CLIP_SPEC.md)
--
-- 영상은 스트리머 PC의 OBS 로컬 녹화본에서 Electron이 잘라 올린다. 서버는
-- 녹화하지 않고 ffmpeg도 실행하지 않는다. 서버가 기억해야 하는 것은 두 가지다.
--   1) 녹화본이 어디 있고 그 타임코드가 wall clock의 어디에 붙는가 (recordings)
--   2) 어떤 하이라이트가 아직 클립이 없는가 (highlights.clip_status)
--
-- 별도 clip_jobs 테이블을 두지 않는 이유
--   작업 대상은 "clip_status = PENDING인 highlights" 그 자체다. 테이블을 나누면
--   highlights와 1:1인 행을 두 곳에서 동기화해야 하고, 리포트가 재생성되어
--   highlights가 교체될 때 고아 작업이 남는다.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) recordings : 녹화본 위치와 시간축 기준점
--
-- 세션당 한 행이라 session_id를 그대로 PK로 쓴다. 한 방송에서 OBS 녹화를
-- 두 번 돌리는 것은 지원하지 않는다 — 그렇게 되면 타임코드가 리셋되어
-- 어느 파일의 몇 초인지 구분할 수 없다(CLIP_SPEC.md §10 미결).
-- -----------------------------------------------------------------------------
CREATE TABLE recordings (
    session_id       BIGINT      PRIMARY KEY REFERENCES broadcast_sessions (id) ON DELETE CASCADE,

    -- 스트리머 PC 기준 절대 경로. 서버는 이 값을 해석하지도 열지도 않는다 —
    -- 다른 컴퓨터의 경로이고, Electron에 그대로 돌려주기 위해서만 보관한다.
    output_path      TEXT,

    -- wall clock ↔ 녹화 타임코드 매핑 기준점.
    -- 녹화 시작 시각을 저장해 두고 빼는 방식을 쓰지 않는 이유는 드리프트다.
    -- OBS 녹화는 프레임 드랍·인코더 지연으로 실제 경과 시각과 서서히 어긋나서,
    -- 시작점에서 멀어질수록 오차가 누적된다. Electron이 60초마다 GetRecordStatus의
    -- 경과 시간을 그때의 wall clock과 함께 보내고 여기에 덮어쓴다.
    base_wall_clock  TIMESTAMPTZ,
    base_timecode_ms BIGINT,

    status           VARCHAR(20) NOT NULL DEFAULT 'RECORDING',
    started_at       TIMESTAMPTZ NOT NULL,
    ended_at         TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_recordings_status
        CHECK (status IN ('RECORDING', 'COMPLETED', 'FAILED')),
    -- 기준점 두 컬럼은 함께 있거나 함께 없다. 한쪽만 있으면 계산이 조용히 틀린다.
    CONSTRAINT ck_recordings_base
        CHECK ((base_wall_clock IS NULL) = (base_timecode_ms IS NULL)),
    CONSTRAINT ck_recordings_base_timecode
        CHECK (base_timecode_ms IS NULL OR base_timecode_ms >= 0),
    CONSTRAINT ck_recordings_period
        CHECK (ended_at IS NULL OR ended_at >= started_at),
    -- 종료된 녹화는 잘라낼 파일이 있어야 한다.
    CONSTRAINT ck_recordings_completed
        CHECK (status <> 'COMPLETED' OR (output_path IS NOT NULL AND ended_at IS NOT NULL))
);

CREATE TRIGGER trg_recordings_updated_at
    BEFORE UPDATE ON recordings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  recordings IS 'OBS 로컬 녹화본 위치와 시간축 기준점 (CLIP_SPEC.md)';
COMMENT ON COLUMN recordings.output_path      IS '스트리머 PC 절대 경로. 서버는 해석하지 않는다';
COMMENT ON COLUMN recordings.base_wall_clock  IS '기준점의 실제 시각. base_timecode_ms와 짝';
COMMENT ON COLUMN recordings.base_timecode_ms IS '기준점의 녹화 경과 시간(ms)';


-- -----------------------------------------------------------------------------
-- 2) highlights : 클립 작업 상태
--
-- clip_url·thumbnail_url은 V1부터 nullable로 있었고 "클립 생성 전·실패 시 NULL"이
-- 이미 계약이다(API 1.5). 여기에 작업 상태만 덧붙인다 — 리포트 파이프라인은
-- 손대지 않으며, 기존 INSERT는 DEFAULT로 PENDING이 된다.
--
--   PENDING  아직 클립 없음. 앱이 가져갈 대상
--   UPLOADED 클립 저장 완료
--   FAILED   재시도 상한 소진. 더 시도하지 않는다
--   SKIPPED  녹화본이 없다(미실행·삭제). 재시도해도 결과가 같다
-- -----------------------------------------------------------------------------
ALTER TABLE highlights
    ADD COLUMN clip_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN clip_attempts      INT         NOT NULL DEFAULT 0,
    ADD COLUMN clip_failed_reason VARCHAR(200),

    ADD CONSTRAINT ck_highlights_clip_status
        CHECK (clip_status IN ('PENDING', 'UPLOADED', 'FAILED', 'SKIPPED')),
    ADD CONSTRAINT ck_highlights_clip_attempts
        CHECK (clip_attempts >= 0),
    -- URL이 있다는 것은 업로드가 끝났다는 뜻이다. 상태와 URL이 어긋나면
    -- 조회 화면과 작업 목록이 서로 다른 말을 하게 된다.
    ADD CONSTRAINT ck_highlights_clip_url_status
        CHECK (clip_url IS NULL OR clip_status = 'UPLOADED');

-- 앱이 미완료 작업을 훑는 경로. 리포트에서 하이라이트로 내려오는 조인을 태우므로
-- report_id를 선두에 두고, PENDING만 담는 부분 인덱스로 크기를 줄인다.
CREATE INDEX ix_highlights_clip_pending ON highlights (report_id)
    WHERE clip_status = 'PENDING';

COMMENT ON COLUMN highlights.clip_status   IS 'PENDING/UPLOADED/FAILED/SKIPPED (CLIP_SPEC.md)';
COMMENT ON COLUMN highlights.clip_attempts IS '클립 생성 실패 횟수. laive.clip.max-attempts에서 FAILED 확정';

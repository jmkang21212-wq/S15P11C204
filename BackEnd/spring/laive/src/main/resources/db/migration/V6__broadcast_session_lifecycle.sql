-- =============================================================================
-- V6: 방송 세션 생명주기와 Transactional Outbox
-- =============================================================================

-- 과거 FAILED 데이터가 있더라도 최종 상태의 종료 시각 제약을 안전하게 적용한다.
UPDATE broadcast_sessions
   SET ended_at = COALESCE(ended_at, updated_at, created_at)
 WHERE status = 'FAILED'
   AND ended_at IS NULL;

ALTER TABLE broadcast_sessions
    DROP CONSTRAINT ck_broadcast_sessions_ended_at;

ALTER TABLE broadcast_sessions
    ADD CONSTRAINT ck_broadcast_sessions_terminal_ended_at
        CHECK (status NOT IN ('ENDED', 'FAILED') OR ended_at IS NOT NULL),
    ADD CONSTRAINT ck_broadcast_sessions_title_not_blank
        CHECK (btrim(title) <> '');

-- 상태 필터가 있는 방송 이력 조회를 위한 인덱스다.
CREATE INDEX ix_broadcast_sessions_streamer_status_started
    ON broadcast_sessions (streamer_id, status, started_at DESC, id DESC);

CREATE TABLE broadcast_session_outbox (
    event_id          UUID         PRIMARY KEY,
    aggregate_id      BIGINT       NOT NULL REFERENCES broadcast_sessions (id) ON DELETE CASCADE,
    event_type        VARCHAR(60)  NOT NULL,
    schema_version    SMALLINT     NOT NULL DEFAULT 1,
    deduplication_key VARCHAR(150) NOT NULL,
    payload           JSONB        NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts          INT          NOT NULL DEFAULT 0,
    available_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until      TIMESTAMPTZ,
    locked_by         VARCHAR(100),
    published_at      TIMESTAMPTZ,
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_broadcast_session_outbox_deduplication_key
        UNIQUE (deduplication_key),
    CONSTRAINT ck_broadcast_session_outbox_event_type
        CHECK (event_type IN (
            'PREPARATION_REQUESTED',
            'BROADCAST_STARTED',
            'BROADCAST_ENDED'
        )),
    CONSTRAINT ck_broadcast_session_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_broadcast_session_outbox_attempts
        CHECK (attempts >= 0)
);

CREATE INDEX ix_broadcast_session_outbox_pending
    ON broadcast_session_outbox (status, available_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

COMMENT ON TABLE broadcast_session_outbox
    IS '방송 세션 상태 변경과 같은 트랜잭션에 저장되는 전달 보장 이벤트';


-- Timescale hypertables and columnstore-backed chunks are changed outside a Flyway transaction.

-- Hypercore permits nullable columns on a columnstore-enabled hypertable, but
-- rejects a column definition that embeds constraints (including REFERENCES).
-- Add the columns first and attach constraints in a separate ALTER statement.
-- This keeps existing columnstore chunks online and avoids a full rowstore
-- conversion during an expand migration.
ALTER TABLE chat_messages
    ADD COLUMN platform_session_id BIGINT,
    ADD COLUMN platform_message_id VARCHAR(512),
    ADD COLUMN message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN amount NUMERIC(20, 4),
    ADD COLUMN currency VARCHAR(10),
    ADD COLUMN provider_event_type VARCHAR(100),
    ADD CONSTRAINT ck_chat_messages_type
        CHECK (message_type IN ('TEXT', 'DONATION', 'MEMBERSHIP', 'GIFT', 'SYSTEM', 'OTHER')),
    ADD CONSTRAINT ck_chat_messages_amount CHECK (amount IS NULL OR amount >= 0);

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_platform_session
        FOREIGN KEY (platform_session_id)
        REFERENCES broadcast_platform_sessions (id) ON DELETE CASCADE;

ALTER TABLE viewer_metrics
    ADD COLUMN platform_session_id BIGINT;
ALTER TABLE viewer_metrics
    ADD CONSTRAINT fk_viewer_metrics_platform_session
        FOREIGN KEY (platform_session_id)
        REFERENCES broadcast_platform_sessions (id) ON DELETE CASCADE;
ALTER TABLE collection_gaps
    ADD COLUMN platform_session_id BIGINT
        REFERENCES broadcast_platform_sessions (id) ON DELETE CASCADE;
ALTER TABLE triggers
    ADD COLUMN platform_session_id BIGINT
        REFERENCES broadcast_platform_sessions (id) ON DELETE SET NULL;
ALTER TABLE highlights
    ADD COLUMN platform_session_id BIGINT
        REFERENCES broadcast_platform_sessions (id) ON DELETE SET NULL;

UPDATE chat_messages m
   SET platform_session_id = p.id
  FROM broadcast_platform_sessions p
 WHERE p.broadcast_session_id = m.session_id;
UPDATE viewer_metrics m
   SET platform_session_id = p.id
  FROM broadcast_platform_sessions p
 WHERE p.broadcast_session_id = m.session_id;
UPDATE collection_gaps g
   SET platform_session_id = p.id
  FROM broadcast_platform_sessions p
 WHERE p.broadcast_session_id = g.session_id;
UPDATE triggers t
   SET platform_session_id = p.id
  FROM broadcast_platform_sessions p
 WHERE p.broadcast_session_id = t.session_id;
UPDATE highlights h
   SET platform_session_id = p.id
  FROM reports r
  JOIN broadcast_platform_sessions p ON p.broadcast_session_id = r.session_id
 WHERE r.id = h.report_id;

ALTER TABLE viewer_metrics DROP CONSTRAINT uq_viewer_metrics_session_measured;
ALTER TABLE collection_gaps DROP CONSTRAINT uq_collection_gaps_session_started;

CREATE UNIQUE INDEX uq_viewer_metrics_platform_measured
    ON viewer_metrics (platform_session_id, measured_at)
    WHERE platform_session_id IS NOT NULL;
CREATE UNIQUE INDEX uq_collection_gaps_platform_started
    ON collection_gaps (platform_session_id, started_at)
    WHERE platform_session_id IS NOT NULL;
CREATE UNIQUE INDEX uq_chat_messages_platform_external
    ON chat_messages (platform_session_id, platform_message_id, sent_at)
    WHERE platform_session_id IS NOT NULL AND platform_message_id IS NOT NULL;

CREATE INDEX ix_chat_messages_platform_sent
    ON chat_messages (platform_session_id, sent_at, id);
CREATE INDEX ix_viewer_metrics_platform_measured
    ON viewer_metrics (platform_session_id, measured_at DESC);

CREATE MATERIALIZED VIEW chat_message_counts_by_platform_1m
WITH (
    timescaledb.continuous,
    timescaledb.materialized_only = false
) AS
SELECT platform_session_id,
       time_bucket(INTERVAL '1 minute', sent_at) AS bucket,
       count(*) AS message_count
  FROM chat_messages
 WHERE platform_session_id IS NOT NULL
 GROUP BY platform_session_id, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy(
    'chat_message_counts_by_platform_1m',
    start_offset => INTERVAL '1 hour',
    end_offset => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => true
);

COMMENT ON VIEW chat_message_counts_by_platform_1m
    IS 'Per-platform-session minute chat volume for multi-platform analysis.';

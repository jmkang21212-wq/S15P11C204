ALTER TABLE triggers
    ADD COLUMN analysis_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN analysis_error VARCHAR(200),
    ADD CONSTRAINT ck_triggers_analysis_status
        CHECK (analysis_status IN ('NOT_REQUESTED', 'PENDING', 'COMPLETED', 'FAILED'));

CREATE INDEX ix_triggers_session_analysis_status
    ON triggers (session_id, analysis_status);

CREATE UNIQUE INDEX uq_highlights_report_trigger
    ON highlights (report_id, trigger_id)
    WHERE trigger_id IS NOT NULL;

COMMENT ON COLUMN triggers.analysis_status
    IS '세션 리포트용 트리거 narrative 생성 상태';
COMMENT ON COLUMN triggers.analysis_error
    IS '원문을 포함하지 않는 최종 분석 오류 종류';

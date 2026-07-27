-- =============================================================================
-- V4: 보관 기간(Retention) 정책
--
-- ⚠ 이 파일의 정책은 오래된 청크를 "복구 불가하게 삭제"한다.
--   API 명세서 16장의 체크리스트 "음성, 채팅, STT 전문의 보관 기간"이
--   확정되면 값을 조정하고, 필요 없는 정책은 remove_retention_policy로 제거한다.
--
--   정책 확인 : SELECT * FROM timescaledb_information.jobs
--                WHERE proc_name = 'policy_retention';
--   정책 제거 : SELECT remove_retention_policy('chat_messages');
-- =============================================================================


-- -----------------------------------------------------------------------------
-- chat_messages : 90일
--
-- 프로젝트 명세서 10.2 "세션당 수만~수십만 건이 쌓이므로 보관 기간(예: 90일)
-- 정책을 적용한다" 에 따른다.
--
-- 영향: 90일이 지난 세션은 GET /highlights/{id}/chat-replay 가 빈 목록을 반환한다.
--       리포트의 total_chat·채팅 피크는 reports / chat_message_counts_1m 에
--       이미 집계되어 있으므로 영향을 받지 않는다.
-- -----------------------------------------------------------------------------
SELECT add_retention_policy('chat_messages', INTERVAL '90 days', if_not_exists => true);


-- -----------------------------------------------------------------------------
-- live_briefings : 90일
--
-- 브리핑 피드는 방송 중 실시간 콘솔에서만 사용하고,
-- 방송 후에는 세션 리포트가 대체한다.
-- -----------------------------------------------------------------------------
SELECT add_retention_policy('live_briefings', INTERVAL '90 days', if_not_exists => true);


-- -----------------------------------------------------------------------------
-- 아래는 보관 기간 확정 전까지 비활성화한다.
--
-- viewer_metrics : 프로젝트 명세서 10.2에 보관 기간 대상으로 명시되어 있으나,
--                  세션 리포트의 viewerTimeline(SC-06)이 이 원본을 그대로 조회한다.
--                  과거 리포트를 언제까지 온전히 보여줄지 정한 뒤 활성화할 것.
-- -----------------------------------------------------------------------------
SELECT add_retention_policy('viewer_metrics',      INTERVAL '90 days', if_not_exists => true);
SELECT add_retention_policy('sentiment_snapshots', INTERVAL '30 days', if_not_exists => true);


-- -----------------------------------------------------------------------------
-- transcripts는 hypertable이 아니므로 add_retention_policy를 쓸 수 없다.
-- STT 전문의 보관 기간이 확정되면 별도 배치나 pg_cron으로 삭제한다.
--   DELETE FROM transcripts t USING broadcast_sessions bs
--    WHERE bs.id = t.session_id AND bs.ended_at < now() - INTERVAL '180 days';
-- 이때 risk_events / promises 는 ON DELETE CASCADE 로 함께 지워지고,
-- report_segments.transcript_id 는 NULL 로 바뀐다.
-- NFR-S-04가 규정하는 "방송 음성 원본" 삭제와는 별개 항목이다.
-- -----------------------------------------------------------------------------

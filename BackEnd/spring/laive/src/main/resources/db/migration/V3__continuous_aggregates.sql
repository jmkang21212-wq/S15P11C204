-- =============================================================================
-- V3: 연속 집계(Continuous Aggregate)
--
-- 분당 채팅량 시계열. 다음 세 곳에서 chat_messages 원본을 풀스캔하지 않게 해준다.
--   1) FR-R-01 하이라이트 구간 탐지 (z-score 입력)
--      → POST /internal/v1/ai/highlight-detection 의 chatCountsObjectKey 생성
--   2) 세션 리포트의 채팅 피크 구간 산출 / reports.total_chat 집계
--   3) 성장 리포트의 기간별 누적 채팅 수
--
-- 실시간 호응도(FR-F-05)와 3분 여론 요약 윈도우(FR-F-01)는
-- 명세서 10.2에 따라 Redis 슬라이딩 윈도우에서 처리하며 여기서 다루지 않는다.
--
-- CREATE MATERIALIZED VIEW ... WITH (timescaledb.continuous)는
-- 트랜잭션 블록 안에서 실행할 수 없다. 동봉된 .conf 파일 참고.
-- =============================================================================

CREATE MATERIALIZED VIEW chat_message_counts_1m
WITH (
    timescaledb.continuous,
    -- false: 아직 구체화되지 않은 최근 구간은 원본에서 실시간으로 합쳐 반환한다.
    --        방송 종료 직후 리포트를 생성해도 마지막 1분이 누락되지 않는다.
    timescaledb.materialized_only = false
) AS
SELECT
    session_id,
    time_bucket(INTERVAL '1 minute', sent_at) AS bucket,
    count(*)                                  AS message_count
FROM chat_messages
GROUP BY session_id, bucket
WITH NO DATA;

-- 인덱스는 따로 만들지 않는다.
-- TimescaleDB가 연속 집계를 만들 때 GROUP BY 컬럼과 time_bucket 컬럼을 묶은
-- 복합 인덱스((session_id, bucket))를 내부 구체화 hypertable에 자동 생성한다.

-- 방송 중에도 콘솔·분석이 최신값을 쓸 수 있도록 1분 주기로 구체화한다.
--   start_offset 1시간 : 지연 도착한 채팅 배치까지 다시 계산
--   end_offset   1분   : 아직 쌓이는 중인 현재 버킷은 구체화하지 않는다
SELECT add_continuous_aggregate_policy(
    'chat_message_counts_1m',
    start_offset      => INTERVAL '1 hour',
    end_offset        => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists     => true
);

-- 연속 집계는 카탈로그상 VIEW로 노출되므로 COMMENT ON VIEW를 사용한다
-- (COMMENT ON MATERIALIZED VIEW는 오류가 난다).
COMMENT ON VIEW chat_message_counts_1m
    IS '세션별 분당 채팅 수. 하이라이트 탐지·리포트 집계용 연속 집계';

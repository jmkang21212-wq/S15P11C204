-- =============================================================================
-- V2: TimescaleDB hypertable 전환 (TimescaleDB 2.28 / PostgreSQL 18)
--
-- 대상 선정 기준: 방송 중 append-only로 계속 쌓이는 시계열 테이블
--
--   chat_messages       sent_at      1일   최대 볼륨 (NFR-P-03: 세션당 초당 50건)
--   viewer_metrics      measured_at  7일   시청자 수 시계열
--   sentiment_snapshots captured_at  7일   감정 비율 스냅샷
--   live_briefings      created_at   30일  실시간 브리핑 피드
--
-- hypertable로 만들지 않은 실시간성 테이블
--   transcripts        : 세션당 약 700행(15초 청크 × 3시간)이라 파티셔닝 이득이 없다.
--                        hypertable은 FK 참조 대상이 될 수 없어
--                        (참조 컬럼에 파티션 키를 포함한 UNIQUE 인덱스가 필요하다)
--                        risk_events / promises / report_segments 의 FK 3개를
--                        포기해야 하는데, 그만한 값어치가 없다.
--   risk_events        : 세션당 수 건 수준. 파티셔닝 이득이 없다
--   repeated_questions : 이력이 아니라 "현재 Top N 상태"로 전량 교체된다 (API 13.6)
--
-- 이 마이그레이션은 트랜잭션 밖에서 실행한다
-- (add_columnstore_policy는 PROCEDURE이며 TimescaleDB DDL 일부가
--  명시적 트랜잭션 블록을 허용하지 않는다). 동봉된 .conf 파일 참고.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) hypertable 전환
-- -----------------------------------------------------------------------------

SELECT create_hypertable(
    'chat_messages',
    by_range('sent_at', INTERVAL '1 day'),
    if_not_exists => true
);

SELECT create_hypertable(
    'viewer_metrics',
    by_range('measured_at', INTERVAL '7 days'),
    if_not_exists => true
);

SELECT create_hypertable(
    'sentiment_snapshots',
    by_range('captured_at', INTERVAL '7 days'),
    if_not_exists => true
);

SELECT create_hypertable(
    'live_briefings',
    by_range('created_at', INTERVAL '30 days'),
    if_not_exists => true
);


-- -----------------------------------------------------------------------------
-- 2) 컬럼스토어(압축) 설정
--
--    볼륨이 큰 두 테이블에만 적용한다.
--    segmentby를 session_id로 두면 "특정 세션의 특정 기간" 조회
--    (채팅 리플레이, 리포트 집계)에서 필요한 세그먼트만 해제하면 된다.
--
--    TimescaleDB 2.18 미만에서는 아래 legacy 문법을 사용한다.
--      ALTER TABLE chat_messages SET (
--          timescaledb.compress,
--          timescaledb.compress_segmentby = 'session_id',
--          timescaledb.compress_orderby   = 'sent_at DESC');
--      SELECT add_compression_policy('chat_messages', INTERVAL '7 days');
-- -----------------------------------------------------------------------------

-- orderby에 id를 포함하는 이유:
-- PK가 (id, sent_at)이라 id가 segmentby/orderby에 없으면
-- 압축 청크에 INSERT할 때 유니크 검사를 위해 세그먼트를 풀어야 한다
-- (TimescaleDB가 "column id should be used for segmenting or ordering" 경고를 낸다).
ALTER TABLE chat_messages SET (
    timescaledb.enable_columnstore = true,
    timescaledb.segmentby          = 'session_id',
    timescaledb.orderby            = 'sent_at DESC, id'
);

CALL add_columnstore_policy('chat_messages', after => INTERVAL '7 days', if_not_exists => true);


ALTER TABLE viewer_metrics SET (
    timescaledb.enable_columnstore = true,
    timescaledb.segmentby          = 'session_id',
    timescaledb.orderby            = 'measured_at DESC, id'
);

CALL add_columnstore_policy('viewer_metrics', after => INTERVAL '7 days', if_not_exists => true);

-- sentiment_snapshots는 행 수 자체는 적지만(3분 윈도우 × 3시간 = 세션당 60행)
-- 비율 3개가 numeric(4,3)이라 행당 크기가 viewer_metrics의 5배다.
-- (TimescaleDB는 정수·타임스탬프에 delta 인코딩을 쓰지만 numeric은 가변길이라
--  일반 압축으로 폴백한다 — 실측 압축비 3.3배, viewer_metrics는 16.3배)
-- 압축비가 낮아도 3배는 줄어들므로 적용한다.
ALTER TABLE sentiment_snapshots SET (
    timescaledb.enable_columnstore = true,
    timescaledb.segmentby          = 'session_id',
    timescaledb.orderby            = 'captured_at DESC, id'
);

CALL add_columnstore_policy('sentiment_snapshots', after => INTERVAL '7 days', if_not_exists => true);

-- live_briefings는 세션당 수십 행 수준이라 컬럼스토어를 적용하지 않는다.

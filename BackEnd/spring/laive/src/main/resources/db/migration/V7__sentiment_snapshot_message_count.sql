-- =============================================================================
-- LAIVE - 1인 스트리머 AI 매니저
-- V7: sentiment_snapshots 윈도우 채팅량
--
-- 배경
--   세션 리포트의 reports.positive_ratio / neutral_ratio / negative_ratio는
--   리포트 시점에 다시 분석하지 않고, 방송 중 3분 윈도우마다
--   POST /internal/v1/ai/chat-summary 결과로 쌓인 sentiment_snapshots를
--   재집계해 확정한다 (API 명세 13.7).
--
--   그런데 윈도우마다 채팅량 차이가 크다. 한산한 구간의 몇 건짜리 윈도우와
--   피크 구간의 수천 건짜리 윈도우를 단순 평균하면 두 윈도우가 같은 비중을
--   갖고, 방송 중 여론이 출렁인 세션일수록 리포트 숫자가 실제와 어긋난다.
--   가중 평균을 쓰려면 그 윈도우의 채팅 건수가 필요하다.
--
--   Spring은 chat-summary 요청에 담아 보낸 messages의 개수를 스냅샷과
--   같은 트랜잭션에서 이 컬럼에 기록한다. FastAPI 응답 형식은 바뀌지 않는다.
--
-- NOT NULL을 걸지 않는 이유
--   이미 저장된 스냅샷의 윈도우 채팅량은 복원할 수 없다. 0으로 채우면
--   "채팅이 없던 윈도우"와 "가중치를 모르는 윈도우"가 구별되지 않고,
--   가중 평균에서 과거 스냅샷이 조용히 사라진다. NULL로 두고,
--   가중치를 하나도 구할 수 없는 세션에서만 집계 쿼리가 단순 평균으로
--   되돌아가게 한다.
--
--   sentiment_snapshots는 V2에서 hypertable로 전환하고 7일 뒤 컬럼스토어로
--   압축한다. 기본값 없는 nullable 컬럼 추가는 압축 청크를 다시 쓰지 않고,
--   신규 컬럼은 전부 NULL이므로 CHECK 검증도 기존 행을 훑지 않는다.
-- =============================================================================

ALTER TABLE sentiment_snapshots
    ADD COLUMN message_count INT
        CONSTRAINT ck_sentiment_snapshots_message_count CHECK (message_count >= 0);

COMMENT ON COLUMN sentiment_snapshots.message_count
    IS 'ERD 외 추가 컬럼. 해당 윈도우의 분석 대상 채팅 건수. 리포트 감정 비율의 가중 평균 가중치다';

-- -----------------------------------------------------------------------------
-- LIVE_BRIEFINGS.promise_id : PROMISE 브리핑을 promises 행과 연결
--
-- 컷오버 병합(2026-07-31) 후 콘솔의 실시간 PROMISE 카드는 AI WS가 전담하고,
-- 스냅샷(API 10.6)은 새로고침·재입장 복구용 히스토리를 맡는다. 히스토리로
-- 복구된 카드에서도 약속 확정/무시(API 10.8)를 누르려면 브리핑이 promises.id를
-- 실어야 한다. WS 경로의 promise_id는 AI 서버가 따로 싣는다.
--
-- RISK·CHAT_SUMMARY 브리핑에는 대응 약속이 없으므로 NULL을 허용한다.
-- ON DELETE SET NULL — 약속이 지워져도(스트리머·세션·전사 CASCADE) 피드 문구는
-- 남기고 카드만 비활성화되게 한다.
-- -----------------------------------------------------------------------------
ALTER TABLE live_briefings
    ADD COLUMN promise_id BIGINT REFERENCES promises (id) ON DELETE SET NULL;

-- promises 삭제(SET NULL 전파) 때 hypertable 전체 스캔을 피한다.
-- 값이 있는 행은 PROMISE 유형뿐이라 부분 인덱스로 충분하다.
CREATE INDEX ix_live_briefings_promise_id
    ON live_briefings (promise_id)
    WHERE promise_id IS NOT NULL;

COMMENT ON COLUMN live_briefings.promise_id IS
    'PROMISE 브리핑이 가리키는 promises.id. RISK·CHAT_SUMMARY는 NULL';

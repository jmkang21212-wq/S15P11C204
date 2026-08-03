-- =============================================================================
-- LAIVE - 1인 스트리머 AI 매니저
-- V21: 약속 → 일정 전환 (promises ↔ calendar_events 연결)
--
-- 방송 중 AI가 감지한 약속을 스트리머가 캘린더 일정으로 확정하는 흐름이다.
-- 지금까지 두 테이블은 서로를 모르는 남남이라, 프론트가 "일정 생성"과
-- "약속 상태 변경"을 따로 호출하는 방법밖에 없었다. 그래서 다음이 깨졌다.
--
--   1) 일정을 취소해도 원래 약속을 되돌릴 방법이 없다. 약속은 전환 시점의
--      상태로 남아 어느 화면에도 안 나오는 미아가 된다.
--   2) 호출이 두 번이라 원자성이 없다. 일정은 만들어졌는데 약속 상태 변경이
--      실패하면 중간 상태로 굳는다.
--   3) 같은 약속을 두 번 전환하는 것을 막을 수 없다.
--
-- 이 마이그레이션은 (1)(3)을 DB 차원에서 막고, (2)를 한 트랜잭션으로 묶을 수
-- 있게 참조 관계를 만든다.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1) calendar_events.promise_id : 이 일정이 어느 약속에서 나왔는가
--
-- NULL이면 사용자가 직접 만든 일정이다. 지금까지의 모든 일정이 여기 해당하므로
-- 기존 행은 손대지 않는다.
--
-- ON DELETE SET NULL인 이유
--   약속이 지워져도 이미 잡아 둔 일정은 남아야 한다. 방송 예정은 시청자에게
--   공지된 약속이고, 출처가 사라졌다는 이유로 일정까지 사라지면 안 된다.
--   CASCADE로 두면 약속 삭제가 조용히 방송 일정을 지운다.
-- -----------------------------------------------------------------------------
ALTER TABLE calendar_events
    ADD COLUMN promise_id BIGINT REFERENCES promises (id) ON DELETE SET NULL;

-- 한 약속은 활성 일정을 둘 이상 가질 수 없다.
-- 취소된 일정(CANCELLED)을 제외해야 "취소 후 다른 날짜로 다시 잡기"가 가능하다.
-- 부분 UNIQUE 인덱스라 promise_id가 NULL인 직접 생성 일정끼리는 서로 충돌하지 않는다.
-- 이 인덱스는 약속 → 활성 일정 역참조(응답의 calendarEventId) 조회도 함께 태운다.
CREATE UNIQUE INDEX ux_calendar_events_promise
    ON calendar_events (promise_id)
    WHERE promise_id IS NOT NULL AND status <> 'CANCELLED';

COMMENT ON COLUMN calendar_events.promise_id
    IS '전환의 출처가 된 약속. NULL이면 사용자가 직접 만든 일정';


-- -----------------------------------------------------------------------------
-- 2) promises.status에 SCHEDULED 추가
--
-- 전환된 약속의 의미는 "일정이 잡혔고 아직 지키지 않았다"다. 기존 상태 중
-- 맞는 것이 없다 — COMPLETED는 이행 완료라 거짓이고, ON_HOLD는 보류라 뜻이
-- 다르다. 상태를 새로 두지 않으면 전환된 약속이 미이행 집계에서 빠지거나
-- 이미 지킨 것으로 잘못 집계된다.
--
-- 캘린더 표시에서 SCHEDULED는 빠진다(PromiseMapper.findCalendarRange).
-- 같은 날짜에 약속 칩과 일정이 겹쳐 두 번 보이면 안 되고, 전환된 약속은
-- 일정이 대신 보이기 때문이다.
--
-- CHECK 제약은 ADD 할 수 없어 DROP 후 다시 만든다. 값 집합만 넓히는 변경이라
-- 기존 행은 모두 새 제약을 통과한다.
-- -----------------------------------------------------------------------------
ALTER TABLE promises
    DROP CONSTRAINT ck_promises_status;

ALTER TABLE promises
    ADD CONSTRAINT ck_promises_status
        CHECK (status IN ('DETECTED', 'PENDING', 'ON_HOLD', 'SCHEDULED', 'COMPLETED', 'DISMISSED'));

COMMENT ON COLUMN promises.status
    IS 'DETECTED/PENDING/ON_HOLD/SCHEDULED/COMPLETED/DISMISSED. SCHEDULED는 일정이 잡힌 미이행 약속';

-- 전환 시 due_date는 건드리지 않는다(NULL 유지). 일정 취소로 PENDING으로 돌아왔을 때
-- "기한 미정" 목록에 다시 나타나 사용자가 새 날짜를 정할 수 있어야 하기 때문이다.
-- due_date를 채워 두면 취소 후에도 캘린더 칩으로만 남아 목록에 오지 않는다.

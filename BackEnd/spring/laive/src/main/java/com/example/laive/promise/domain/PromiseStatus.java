package com.example.laive.promise.domain;

/**
 * 약속의 진행 상태다 (API 10.8).
 *
 * <p>AI가 감지하면 DETECTED로 들어오고, 스트리머가 확정하면 PENDING, 무시하면
 * DISMISSED가 된다. 확정한 약속을 캘린더 일정으로 전환하면 SCHEDULED가 되고,
 * 그 일정을 취소하면 PENDING으로 돌아온다.
 */
public enum PromiseStatus {
    DETECTED,
    PENDING,
    ON_HOLD,
    /** 일정이 잡혔고 아직 지키지 않았다. calendar_events에 연결된 행이 있다. */
    SCHEDULED,
    COMPLETED,
    DISMISSED;

    /** 확정·무시는 아직 손대지 않은 약속에만 할 수 있다 (API 10.8.2). */
    public boolean isDetected() {
        return this == DETECTED;
    }

    /**
     * PATCH를 받을 수 있는 현재 상태인지.
     *
     * <p>DETECTED는 확정·무시 전용 API의 몫이고, DISMISSED는 한 번 버린 약속이라
     * 되살리지 않는다. SCHEDULED가 여기 있어야 일정이 잡힌 약속을 COMPLETED·ON_HOLD로
     * 옮길 수 있다.
     */
    public boolean isPatchSource() {
        return this == PENDING || this == ON_HOLD || this == SCHEDULED || this == COMPLETED;
    }

    /**
     * PATCH로 도달할 수 있는 상태인지.
     *
     * <p>도달 가능 집합을 출발 가능 집합과 나눠 둔 이유는 둘이 다르기 때문이다.
     * DISMISSED는 확정한 약속을 지우는 유일한 경로라 도달만 가능해야 하고(전용
     * dismiss API는 DETECTED만 받는다), SCHEDULED는 반대로 출발만 가능해야 한다 —
     * 일정 없이 SCHEDULED가 되면 캘린더에도 목록에도 안 보이는 미아가 생기므로
     * {@code POST /promises/{id}/schedule}만 이 상태를 만들 수 있다.
     */
    public boolean isPatchTarget() {
        return this == PENDING || this == ON_HOLD || this == COMPLETED || this == DISMISSED;
    }

    /** 약속을 일정으로 전환할 수 있는 상태인지. 확정했고 아직 일정이 없어야 한다. */
    public boolean isSchedulable() {
        return this == PENDING || this == ON_HOLD;
    }

    /**
     * 캘린더에 약속 칩으로 표시할 상태인지. 확정 전과 종료된 약속은 올리지 않는다.
     *
     * <p>SCHEDULED가 빠지는 이유는 그 약속이 일정으로 대신 보이기 때문이다. 넣으면
     * 같은 약속이 칩과 일정으로 두 번 그려진다. PromiseMapper.findCalendarRange의
     * status 조건이 같은 규칙을 SQL에 갖고 있으니 함께 봐야 한다.
     */
    public boolean showsOnCalendar() {
        return this == PENDING || this == ON_HOLD;
    }
}

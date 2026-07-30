package com.example.laive.promise.domain;

/**
 * 약속의 진행 상태다 (API 10.8).
 *
 * <p>AI가 감지하면 DETECTED로 들어오고, 스트리머가 확정하면 PENDING, 무시하면
 * DISMISSED가 된다. 확정 이후의 이동(PENDING·COMPLETED·ON_HOLD)만 PATCH로 열려 있다.
 * DETECTED와 DISMISSED는 확정·무시 전용 API로만 도달한다.
 */
public enum PromiseStatus {
    DETECTED,
    PENDING,
    ON_HOLD,
    COMPLETED,
    DISMISSED;

    /** 확정·무시는 아직 손대지 않은 약속에만 할 수 있다 (API 10.8.2). */
    public boolean isDetected() {
        return this == DETECTED;
    }

    /** PATCH로 도달할 수 있는 상태인지. DETECTED·DISMISSED는 전용 API의 몫이다. */
    public boolean isPatchTarget() {
        return this == PENDING || this == COMPLETED || this == ON_HOLD;
    }

    /** 캘린더에 표시할 상태인지. 확정 전과 종료된 약속은 일정에 올리지 않는다. */
    public boolean showsOnCalendar() {
        return this == PENDING || this == ON_HOLD;
    }
}

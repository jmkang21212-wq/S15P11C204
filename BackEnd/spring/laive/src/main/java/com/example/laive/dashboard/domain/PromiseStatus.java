package com.example.laive.dashboard.domain;

public enum PromiseStatus {
    DETECTED,
    PENDING,
    ON_HOLD,
    /** 일정이 잡힌 미이행 약속. 대시보드 미이행 집계에 포함된다. */
    SCHEDULED,
    COMPLETED,
    DISMISSED
}

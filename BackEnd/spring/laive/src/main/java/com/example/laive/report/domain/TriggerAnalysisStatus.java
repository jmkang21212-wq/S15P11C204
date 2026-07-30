package com.example.laive.report.domain;

public enum TriggerAnalysisStatus {
    NOT_REQUESTED,
    PENDING,
    COMPLETED,
    FAILED;

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}

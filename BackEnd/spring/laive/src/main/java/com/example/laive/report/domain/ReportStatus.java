package com.example.laive.report.domain;

public enum ReportStatus {
    PROCESSING,
    COMPLETED,
    PARTIAL,
    FAILED;

    public boolean isTerminal() {
        return this != PROCESSING;
    }

    public boolean canTransitionTo(ReportStatus next) {
        return this == PROCESSING && next != PROCESSING;
    }
}

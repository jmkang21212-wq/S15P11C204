package com.example.laive.report.event;

import com.example.laive.report.domain.ReportStatus;

import java.time.Instant;

public record ReportCompletedEvent(
        long sessionId,
        long reportId,
        ReportStatus status,
        Instant completedAt
) {
}

package com.example.laive.dashboard.domain;

import com.example.laive.report.domain.ReportStatus;

import java.time.Instant;

public record RecentSession(
        long sessionId,
        String title,
        Instant endedAt,
        ReportStatus reportStatus,
        int avgViewers,
        int peakViewers,
        String summary
) {
}

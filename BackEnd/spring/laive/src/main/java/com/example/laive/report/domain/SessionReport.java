package com.example.laive.report.domain;

import java.time.Instant;

public record SessionReport(
        long reportId,
        long sessionId,
        String title,
        Instant startedAt,
        Instant endedAt,
        ReportStatus status,
        String summary,
        Integer avgViewers,
        Integer peakViewers,
        long totalChat,
        SentimentCounts sentimentCounts,
        Instant generatedAt
) {
}

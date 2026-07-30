package com.example.laive.report.repository.mybatis;

import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.SentimentCounts;
import com.example.laive.report.domain.SessionReport;

import java.time.Instant;

public record SessionReportRow(
        long reportId,
        long sessionId,
        String title,
        Instant startedAt,
        Instant endedAt,
        ReportStatus status,
        String summary,
        Integer avgViewers,
        Integer peakViewers,
        Long totalChat,
        Long analyzedChat,
        Long joyCount,
        Long excitementCount,
        Long supportCount,
        Long surpriseCount,
        Long sadnessCount,
        Long angerCount,
        Long confusionCount,
        Long neutralCount,
        Instant generatedAt
) {
    public SessionReport toDomain() {
        return new SessionReport(
                reportId, sessionId, title, startedAt, endedAt, status, summary,
                avgViewers, peakViewers, zero(totalChat),
                new SentimentCounts(
                        zero(analyzedChat), zero(joyCount), zero(excitementCount), zero(supportCount),
                        zero(surpriseCount), zero(sadnessCount), zero(angerCount),
                        zero(confusionCount), zero(neutralCount)
                ),
                generatedAt
        );
    }

    private static long zero(Long value) {
        return value == null ? 0 : value;
    }
}

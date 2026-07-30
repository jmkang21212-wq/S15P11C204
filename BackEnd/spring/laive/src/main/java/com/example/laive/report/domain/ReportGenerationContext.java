package com.example.laive.report.domain;

public record ReportGenerationContext(
        long reportId,
        long sessionId,
        String title,
        String category,
        String language,
        long durationMinutes,
        SessionReport report,
        boolean hasCollectionGap
) {
}

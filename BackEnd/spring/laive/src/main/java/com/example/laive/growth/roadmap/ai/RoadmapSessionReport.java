package com.example.laive.growth.roadmap.ai;

import org.apache.ibatis.annotations.AutomapConstructor;

import java.time.Instant;

public record RoadmapSessionReport(
        long reportId,
        long sessionId,
        String title,
        String category,
        Instant endedAt,
        String status,
        Integer avgViewers,
        Integer peakViewers,
        long totalChat,
        long analyzedChat,
        long broadcastMinutes,
        String summary,
        String insights,
        String triggers,
        String highlights,
        String emotionSummary,
        String riskSummary,
        String platformBreakdown,
        boolean collectionGap
) {
    @AutomapConstructor
    public RoadmapSessionReport {
        platformBreakdown = platformBreakdown == null ? "" : platformBreakdown;
    }

    public RoadmapSessionReport(
            long reportId,
            long sessionId,
            String title,
            String category,
            Instant endedAt,
            String status,
            Integer avgViewers,
            Integer peakViewers,
            long totalChat,
            long analyzedChat,
            long broadcastMinutes,
            String summary,
            String insights,
            String triggers,
            String highlights,
            String emotionSummary,
            String riskSummary,
            boolean collectionGap
    ) {
        this(reportId, sessionId, title, category, endedAt, status,
                avgViewers, peakViewers, totalChat, analyzedChat, broadcastMinutes,
                summary, insights, triggers, highlights, emotionSummary, riskSummary,
                "", collectionGap);
    }
}

package com.example.laive.growth.roadmap.ai;

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
        boolean collectionGap
) {
}

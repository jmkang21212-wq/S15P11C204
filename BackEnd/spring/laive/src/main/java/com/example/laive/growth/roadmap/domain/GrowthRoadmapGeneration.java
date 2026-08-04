package com.example.laive.growth.roadmap.domain;

import java.time.Instant;

public record GrowthRoadmapGeneration(
        long id,
        long roadmapId,
        Long sourceReportId,
        RoadmapGenerationTrigger triggerType,
        RoadmapGenerationStatus status,
        RoadmapQuality quality,
        String promptVersion,
        String idempotencyKey,
        String inputFingerprint,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String errorCode
) {
}

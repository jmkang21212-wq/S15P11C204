package com.example.laive.growth.roadmap.domain;

import java.time.Instant;

public record GrowthRoadmap(
        long id,
        long streamerId,
        RoadmapStatus status,
        RoadmapQuality quality,
        Long currentGenerationId,
        Long sourceReportId,
        String promptVersion,
        boolean stale,
        Instant generatedAt,
        String generationError
) {
}

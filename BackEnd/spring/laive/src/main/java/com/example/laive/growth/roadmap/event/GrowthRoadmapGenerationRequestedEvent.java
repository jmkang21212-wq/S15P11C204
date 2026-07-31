package com.example.laive.growth.roadmap.event;

import com.example.laive.growth.roadmap.domain.RoadmapGenerationTrigger;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;

import java.time.Instant;
import java.util.UUID;

public record GrowthRoadmapGenerationRequestedEvent(
        UUID eventId,
        short schemaVersion,
        long generationId,
        long roadmapId,
        long streamerId,
        Long sourceReportId,
        RoadmapGenerationTrigger triggerType,
        RoadmapQuality quality,
        String promptVersion,
        Instant occurredAt
) {
}

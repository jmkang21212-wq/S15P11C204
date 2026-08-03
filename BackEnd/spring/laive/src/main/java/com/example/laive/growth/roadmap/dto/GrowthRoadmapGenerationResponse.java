package com.example.laive.growth.roadmap.dto;

import com.example.laive.growth.roadmap.domain.GrowthRoadmapGeneration;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationStatus;

import java.time.Instant;

public record GrowthRoadmapGenerationResponse(
        long generationId,
        RoadmapGenerationStatus status,
        Instant requestedAt
) {
    public static GrowthRoadmapGenerationResponse from(
            GrowthRoadmapGeneration generation
    ) {
        return new GrowthRoadmapGenerationResponse(
                generation.id(), generation.status(), generation.requestedAt()
        );
    }
}

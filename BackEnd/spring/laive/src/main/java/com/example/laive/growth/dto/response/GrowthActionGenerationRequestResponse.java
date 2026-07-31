package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionGenerationStatus;

import java.time.Instant;

public record GrowthActionGenerationRequestResponse(
        long generationId,
        GrowthActionGenerationStatus status,
        Instant requestedAt
) {
    public static GrowthActionGenerationRequestResponse from(
            GrowthActionGeneration generation
    ) {
        GrowthActionGenerationStatus status = switch (generation.status()) {
            case WAITING_FOR_DATA -> GrowthActionGenerationStatus.WAITING_FOR_DATA;
            case PENDING -> GrowthActionGenerationStatus.PENDING;
            case PROCESSING -> GrowthActionGenerationStatus.PROCESSING;
            case COMPLETED -> GrowthActionGenerationStatus.COMPLETED;
            case FAILED -> GrowthActionGenerationStatus.FAILED;
            case SUPERSEDED -> GrowthActionGenerationStatus.NOT_REQUESTED;
        };
        return new GrowthActionGenerationRequestResponse(
                generation.id(), status, generation.requestedAt()
        );
    }
}

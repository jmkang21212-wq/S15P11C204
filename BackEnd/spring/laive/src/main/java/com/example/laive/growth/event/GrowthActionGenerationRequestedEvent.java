package com.example.laive.growth.event;

import java.time.Instant;
import java.util.UUID;

public record GrowthActionGenerationRequestedEvent(
        UUID eventId,
        short schemaVersion,
        long generationId,
        long growthGoalId,
        long goalRevision,
        Long sourceReportId,
        String promptVersion,
        Instant occurredAt
) {
}

package com.example.laive.growth.domain;

import java.time.Instant;

public record GrowthActionGeneration(
        long id,
        long growthGoalId,
        Long sourceReportId,
        long goalRevision,
        GrowthActionTriggerType triggerType,
        GrowthActionJobStatus status,
        String promptVersion,
        String idempotencyKey,
        String inputFingerprint,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String errorCode
) {
}

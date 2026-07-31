package com.example.laive.growth.domain;

import java.time.Instant;

public record GrowthActionGenerationView(
        GrowthActionGenerationStatus status,
        boolean stale,
        Instant generatedAt,
        Long sourceReportId,
        String promptVersion,
        String errorCode
) {
    public static GrowthActionGenerationView notRequested() {
        return new GrowthActionGenerationView(
                GrowthActionGenerationStatus.NOT_REQUESTED,
                false, null, null, null, null
        );
    }
}

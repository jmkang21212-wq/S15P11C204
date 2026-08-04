package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthActionGenerationStatus;
import com.example.laive.growth.domain.GrowthActionGenerationView;

import java.time.Instant;

public record GrowthActionGenerationResponse(
        GrowthActionGenerationStatus status,
        boolean stale,
        Instant generatedAt,
        Long sourceReportId,
        String promptVersion,
        String errorCode
) {
    public static GrowthActionGenerationResponse from(GrowthActionGenerationView view) {
        return new GrowthActionGenerationResponse(
                view.status(), view.stale(), view.generatedAt(), view.sourceReportId(),
                view.promptVersion(), view.errorCode()
        );
    }
}

package com.example.laive.growth.roadmap.domain;

import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record GrowthGoalSuggestion(
        long id,
        long roadmapId,
        long generationId,
        int displayOrder,
        GrowthMetricType metricType,
        BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        String title,
        String rationale,
        SuggestionStatus status,
        Long acceptedGoalId,
        Instant acceptedAt,
        Instant dismissedAt
) {
}

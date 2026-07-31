package com.example.laive.growth.roadmap.dto;

import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;
import com.example.laive.growth.roadmap.domain.GrowthSuggestionView;
import com.example.laive.growth.roadmap.domain.SuggestionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GrowthGoalSuggestionResponse(
        long suggestionId,
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
        List<GrowthSuggestionActionResponse> actions
) {
    static GrowthGoalSuggestionResponse from(GrowthSuggestionView view) {
        var value = view.suggestion();
        return new GrowthGoalSuggestionResponse(
                value.id(), value.displayOrder(), value.metricType(),
                value.targetValue(), value.unit(), value.periodStart(),
                value.periodEnd(), value.title(), value.rationale(), value.status(),
                value.acceptedGoalId(),
                view.actions().stream().map(GrowthSuggestionActionResponse::from).toList()
        );
    }
}

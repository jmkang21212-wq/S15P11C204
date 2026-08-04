package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthGoalView;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GrowthGoalResponse(
        long growthGoalId,
        GrowthMetricType metricType,
        BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        GrowthGoalStatus status,
        BigDecimal currentValue,
        BigDecimal progressRate,
        List<GrowthActionResponse> actions,
        GrowthActionGenerationResponse actionGeneration
) {
    public static GrowthGoalResponse from(GrowthGoalView view) {
        var goal = view.goal();
        return new GrowthGoalResponse(
                goal.id(),
                goal.metricType(),
                goal.targetValue(),
                goal.unit(),
                goal.periodStart(),
                goal.periodEnd(),
                goal.status(),
                view.currentValue(),
                view.progressRate(),
                view.actions().stream().map(GrowthActionResponse::from).toList(),
                GrowthActionGenerationResponse.from(view.actionGeneration())
        );
    }
}

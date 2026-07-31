package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record SuggestedGrowthGoal(
        GrowthMetricType metricType,
        BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        String title,
        String rationale,
        List<GeneratedGrowthAction> actions
) {
    public SuggestedGrowthGoal {
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (metricType == null || targetValue == null || targetValue.signum() <= 0
                || unit == null || metricType.unit() != unit
                || periodStart == null || periodEnd == null
                || title == null || title.isBlank() || title.length() > 200
                || rationale == null || rationale.isBlank() || rationale.length() > 2000
                || actions.isEmpty() || actions.size() > 3) {
            throw new IllegalArgumentException("Invalid growth roadmap suggestion.");
        }
        long days = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (days < 28 || days > 84) {
            throw new IllegalArgumentException("Suggestion period must be 28-84 days.");
        }
    }
}

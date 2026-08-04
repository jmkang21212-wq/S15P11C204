package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.domain.GrowthMetricType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

public record GrowthRoadmapResult(List<SuggestedGrowthGoal> goals) {
    public GrowthRoadmapResult {
        goals = goals == null ? List.of() : List.copyOf(goals);
        if (goals.size() != 3) {
            throw new IllegalArgumentException(
                    "A roadmap must have exactly three suggestions but had %d."
                            .formatted(goals.size())
            );
        }
        var metrics = new HashSet<GrowthMetricType>();
        for (SuggestedGrowthGoal goal : goals) {
            if (!metrics.add(goal.metricType())) {
                throw new IllegalArgumentException(
                        "Roadmap suggestion metrics must be unique but %s repeated."
                                .formatted(goal.metricType())
                );
            }
        }
    }

    /**
     * 어긴 조건을 메시지에 남긴다. 지표 이름·수치·날짜만 담으므로 채팅 원문은 새지 않는다.
     */
    public void validateAgainst(GrowthRoadmapInput input, LocalDate today) {
        for (SuggestedGrowthGoal goal : goals) {
            BigDecimal current = input.currentValues().get(goal.metricType());
            if (current == null || current.signum() <= 0) {
                throw new IllegalArgumentException(
                        "%s: no positive current value to grow from (current=%s)."
                                .formatted(goal.metricType(), current)
                );
            }
            if (goal.targetValue().compareTo(current) <= 0) {
                throw new IllegalArgumentException(
                        "%s: target %s must be greater than the current value %s."
                                .formatted(goal.metricType(), goal.targetValue(), current)
                );
            }
            BigDecimal ceiling = current.multiply(BigDecimal.valueOf(2));
            if (goal.targetValue().compareTo(ceiling) > 0) {
                throw new IllegalArgumentException(
                        "%s: target %s exceeds twice the current value %s (ceiling %s)."
                                .formatted(goal.metricType(), goal.targetValue(), current, ceiling)
                );
            }
            long offset = ChronoUnit.DAYS.between(today, goal.periodStart());
            if (offset < 0 || offset > 7) {
                throw new IllegalArgumentException(
                        "%s: periodStart %s must fall within 7 days of %s but was %+d days."
                                .formatted(goal.metricType(), goal.periodStart(), today, offset)
                );
            }
        }
    }
}

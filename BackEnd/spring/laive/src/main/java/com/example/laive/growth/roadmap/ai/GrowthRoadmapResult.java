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
            throw new IllegalArgumentException("A roadmap must have exactly three suggestions.");
        }
        var metrics = new HashSet<GrowthMetricType>();
        if (goals.stream().anyMatch(goal -> !metrics.add(goal.metricType()))) {
            throw new IllegalArgumentException("Roadmap suggestion metrics must be unique.");
        }
    }

    public void validateAgainst(GrowthRoadmapInput input, LocalDate today) {
        for (SuggestedGrowthGoal goal : goals) {
            BigDecimal current = input.currentValues().get(goal.metricType());
            if (current == null || current.signum() <= 0
                    || goal.targetValue().compareTo(current) <= 0
                    || goal.targetValue().compareTo(current.multiply(BigDecimal.valueOf(2))) > 0
                    || goal.periodStart().isBefore(today)
                    || ChronoUnit.DAYS.between(today, goal.periodStart()) > 7) {
                throw new IllegalArgumentException("Suggestion exceeds the server guardrail.");
            }
        }
    }
}

package com.example.laive.growth.domain;

import java.math.BigDecimal;
import java.util.List;

public record GrowthGoalView(
        GrowthGoal goal,
        BigDecimal currentValue,
        BigDecimal progressRate,
        List<GrowthAction> actions,
        GrowthActionGenerationView actionGeneration
) {
}

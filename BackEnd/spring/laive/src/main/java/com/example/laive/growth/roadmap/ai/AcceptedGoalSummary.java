package com.example.laive.growth.roadmap.ai;

import java.math.BigDecimal;

public record AcceptedGoalSummary(
        String metricType,
        BigDecimal targetValue,
        BigDecimal currentValue,
        BigDecimal progressRate,
        String status
) {
}

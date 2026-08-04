package com.example.laive.growth.domain;

import java.math.BigDecimal;

public record GrowthGoalMetrics(
        long growthGoalId,
        BigDecimal averageViewers,
        BigDecimal totalChat,
        BigDecimal broadcastMinutes,
        BigDecimal broadcastsPerWeek
) {
    public BigDecimal valueFor(GrowthMetricType type) {
        return switch (type) {
            case AVG_VIEWERS -> averageViewers;
            case TOTAL_CHAT -> totalChat;
            case BROADCAST_MINUTES -> broadcastMinutes;
            case BROADCASTS_PER_WEEK -> broadcastsPerWeek;
        };
    }
}

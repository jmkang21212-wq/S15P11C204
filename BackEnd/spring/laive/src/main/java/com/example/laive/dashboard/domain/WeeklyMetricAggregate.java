package com.example.laive.dashboard.domain;

public record WeeklyMetricAggregate(
        boolean hasCurrentData,
        int currentAvgViewers,
        long currentTotalChat,
        int previousAvgViewers,
        long previousTotalChat
) {
}

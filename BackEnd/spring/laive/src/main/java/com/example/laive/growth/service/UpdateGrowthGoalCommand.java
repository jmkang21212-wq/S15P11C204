package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGrowthGoalCommand(
        GrowthMetricType metricType,
        BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        GrowthGoalStatus status
) {
    public boolean isEmpty() {
        return metricType == null && targetValue == null && unit == null
                && periodStart == null && periodEnd == null && status == null;
    }
}

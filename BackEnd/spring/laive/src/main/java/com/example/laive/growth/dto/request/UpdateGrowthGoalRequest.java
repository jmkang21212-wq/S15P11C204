package com.example.laive.growth.dto.request;

import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;
import com.example.laive.growth.service.UpdateGrowthGoalCommand;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGrowthGoalRequest(
        GrowthMetricType metricType,
        @DecimalMin(value = "0", inclusive = false) BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        GrowthGoalStatus status
) {
    public UpdateGrowthGoalCommand toCommand() {
        return new UpdateGrowthGoalCommand(
                metricType, targetValue, unit, periodStart, periodEnd, status
        );
    }
}

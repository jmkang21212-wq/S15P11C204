package com.example.laive.growth.dto.request;

import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;
import com.example.laive.growth.service.CreateGrowthGoalCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGrowthGoalRequest(
        @NotNull GrowthMetricType metricType,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal targetValue,
        @NotNull GrowthMetricUnit unit,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {
    public CreateGrowthGoalCommand toCommand() {
        return new CreateGrowthGoalCommand(
                metricType, targetValue, unit, periodStart, periodEnd
        );
    }
}

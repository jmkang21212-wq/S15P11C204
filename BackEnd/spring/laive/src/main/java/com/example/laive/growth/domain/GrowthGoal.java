package com.example.laive.growth.domain;

import com.example.laive.growth.exception.GrowthException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record GrowthGoal(
        long id,
        long streamerId,
        GrowthMetricType metricType,
        BigDecimal targetValue,
        GrowthMetricUnit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        GrowthGoalStatus status
) {
    public static GrowthGoal create(
            long streamerId,
            GrowthMetricType metricType,
            BigDecimal targetValue,
            GrowthMetricUnit unit,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        GrowthGoal goal = new GrowthGoal(
                0, streamerId, metricType, targetValue, unit,
                periodStart, periodEnd, GrowthGoalStatus.ACTIVE
        );
        goal.validate();
        return goal;
    }

    public GrowthGoal update(
            GrowthMetricType nextType,
            BigDecimal nextTarget,
            GrowthMetricUnit nextUnit,
            LocalDate nextStart,
            LocalDate nextEnd,
            GrowthGoalStatus nextStatus
    ) {
        if (status == GrowthGoalStatus.CANCELLED) {
            throw GrowthException.stateConflict();
        }
        GrowthGoalStatus resolvedStatus = nextStatus == null ? status : nextStatus;
        if (!status.canTransitionTo(resolvedStatus)) {
            throw GrowthException.stateConflict();
        }
        GrowthGoal updated = new GrowthGoal(
                id, streamerId,
                nextType == null ? metricType : nextType,
                nextTarget == null ? targetValue : nextTarget,
                nextUnit == null ? unit : nextUnit,
                nextStart == null ? periodStart : nextStart,
                nextEnd == null ? periodEnd : nextEnd,
                resolvedStatus
        );
        updated.validate();
        return updated;
    }

    public void validate() {
        if (metricType == null || targetValue == null || targetValue.signum() <= 0
                || unit == null || unit != metricType.unit()
                || periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)
                || ChronoUnit.DAYS.between(periodStart, periodEnd) + 1 > GrowthPeriod.MAX_DAYS
                || status == null) {
            throw GrowthException.invalidGoal();
        }
    }
}

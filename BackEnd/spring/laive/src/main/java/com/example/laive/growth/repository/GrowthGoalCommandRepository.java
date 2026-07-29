package com.example.laive.growth.repository;

import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthMetricType;

import java.time.LocalDate;
import java.util.Optional;

public interface GrowthGoalCommandRepository {
    void lockScope(long streamerId, GrowthMetricType metricType);

    boolean existsOverlappingActive(
            long streamerId,
            GrowthMetricType metricType,
            LocalDate periodStart,
            LocalDate periodEnd,
            Long excludedGoalId
    );

    long insert(GrowthGoal goal);

    Optional<GrowthGoal> findOwnedForUpdate(long streamerId, long goalId);

    int update(GrowthGoal goal);
}

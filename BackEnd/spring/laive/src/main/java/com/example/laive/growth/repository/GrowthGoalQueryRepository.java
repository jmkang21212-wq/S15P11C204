package com.example.laive.growth.repository;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalStatus;

import java.util.List;
import java.util.Optional;

public interface GrowthGoalQueryRepository {
    List<GrowthGoal> findGoals(long streamerId, GrowthGoalStatus status);

    List<GrowthAction> findActions(List<Long> goalIds);

    Optional<GrowthGoal> findOwned(long streamerId, long goalId);
}

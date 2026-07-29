package com.example.laive.growth.repository.mybatis;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface GrowthMapper {
    List<GrowthSeriesPoint> findSeries(
            @Param("streamerId") long streamerId,
            @Param("periodType") GrowthPeriodType periodType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    List<GrowthGoal> findGoals(
            @Param("streamerId") long streamerId,
            @Param("status") GrowthGoalStatus status
    );

    Optional<GrowthGoal> findOwned(
            @Param("streamerId") long streamerId,
            @Param("goalId") long goalId
    );

    Optional<GrowthGoal> findOwnedForUpdate(
            @Param("streamerId") long streamerId,
            @Param("goalId") long goalId
    );

    List<GrowthAction> findActions(@Param("goalIds") List<Long> goalIds);

    List<GrowthGoalMetrics> findGoalMetrics(
            @Param("streamerId") long streamerId,
            @Param("goalIds") List<Long> goalIds
    );

    int lockScope(
            @Param("streamerId") long streamerId,
            @Param("metricType") GrowthMetricType metricType
    );

    boolean existsOverlappingActive(
            @Param("streamerId") long streamerId,
            @Param("metricType") GrowthMetricType metricType,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("excludedGoalId") Long excludedGoalId
    );

    Long insertGoal(@Param("goal") GrowthGoal goal);

    int updateGoal(@Param("goal") GrowthGoal goal);
}

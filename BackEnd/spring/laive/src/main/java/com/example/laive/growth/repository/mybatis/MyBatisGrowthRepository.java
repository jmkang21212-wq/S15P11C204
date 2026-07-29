package com.example.laive.growth.repository.mybatis;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthPeriod;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import com.example.laive.growth.repository.GrowthGoalCommandRepository;
import com.example.laive.growth.repository.GrowthGoalQueryRepository;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGrowthRepository implements
        GrowthReportQueryRepository,
        GrowthGoalQueryRepository,
        GrowthGoalCommandRepository {

    private final GrowthMapper mapper;

    public MyBatisGrowthRepository(GrowthMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<GrowthSeriesPoint> findSeries(
            long streamerId,
            GrowthPeriodType periodType,
            GrowthPeriod period
    ) {
        return mapper.findSeries(streamerId, periodType, period.start(), period.end());
    }

    @Override
    public List<GrowthGoalMetrics> findGoalMetrics(long streamerId, List<Long> goalIds) {
        return goalIds.isEmpty() ? List.of() : mapper.findGoalMetrics(streamerId, goalIds);
    }

    @Override
    public List<GrowthGoal> findGoals(long streamerId, GrowthGoalStatus status) {
        return mapper.findGoals(streamerId, status);
    }

    @Override
    public List<GrowthAction> findActions(List<Long> goalIds) {
        return goalIds.isEmpty() ? List.of() : mapper.findActions(goalIds);
    }

    @Override
    public Optional<GrowthGoal> findOwned(long streamerId, long goalId) {
        return mapper.findOwned(streamerId, goalId);
    }

    @Override
    public void lockScope(long streamerId, GrowthMetricType metricType) {
        mapper.lockScope(streamerId, metricType);
    }

    @Override
    public boolean existsOverlappingActive(
            long streamerId,
            GrowthMetricType metricType,
            LocalDate periodStart,
            LocalDate periodEnd,
            Long excludedGoalId
    ) {
        return mapper.existsOverlappingActive(
                streamerId, metricType, periodStart, periodEnd, excludedGoalId
        );
    }

    @Override
    public long insert(GrowthGoal goal) {
        Long id = mapper.insertGoal(goal);
        if (id == null) {
            throw new IllegalStateException("성장 목표 INSERT 결과에 ID가 없습니다.");
        }
        return id;
    }

    @Override
    public Optional<GrowthGoal> findOwnedForUpdate(long streamerId, long goalId) {
        return mapper.findOwnedForUpdate(streamerId, goalId);
    }

    @Override
    public int update(GrowthGoal goal) {
        return mapper.updateGoal(goal);
    }
}

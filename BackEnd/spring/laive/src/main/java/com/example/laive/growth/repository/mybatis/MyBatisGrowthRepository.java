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
import com.example.laive.growth.repository.GrowthActionRepository;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionJobStatus;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.ai.RecentGrowthReport;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGrowthRepository implements
        GrowthReportQueryRepository,
        GrowthGoalQueryRepository,
        GrowthGoalCommandRepository,
        GrowthActionRepository {

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
        return mapper.findSeries(streamerId, periodType, period.start(), period.end(), "ALL");
    }

    @Override
    public List<GrowthSeriesPoint> findSeries(
            long streamerId,
            GrowthPeriodType periodType,
            GrowthPeriod period,
            String platformCode
    ) {
        return mapper.findSeries(
                streamerId, periodType, period.start(), period.end(), platformCode);
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

    @Override
    public Optional<GrowthActionGeneration> findGeneration(long generationId) {
        return mapper.findGeneration(generationId);
    }

    @Override
    public Optional<GrowthGoal> findGoal(long goalId) {
        return mapper.findGoal(goalId);
    }

    @Override
    public Optional<GrowthActionGeneration> findGenerationForUpdate(long generationId) {
        return mapper.findGenerationForUpdate(generationId);
    }

    @Override
    public List<GrowthActionGeneration> findLatestGenerations(List<Long> goalIds) {
        return goalIds.isEmpty() ? List.of() : mapper.findLatestGenerations(goalIds);
    }

    @Override
    public Optional<GrowthActionGeneration> findRunningGeneration(long goalId) {
        return mapper.findRunningGeneration(goalId);
    }

    @Override
    public boolean hasEligibleReport(long goalId) {
        return mapper.hasEligibleReport(goalId);
    }

    @Override
    public List<RecentGrowthReport> findRecentReports(long goalId, int limit) {
        return mapper.findRecentReports(goalId, limit);
    }

    @Override
    public List<GrowthGoal> findActiveGoalsAffectedByReport(long reportId) {
        return mapper.findActiveGoalsAffectedByReport(reportId);
    }

    @Override
    public long insertGeneration(
            long goalId, Long sourceReportId, long revision,
            GrowthActionTriggerType triggerType, GrowthActionJobStatus status,
            String promptVersion, String idempotencyKey, Instant requestedAt
    ) {
        Long id = mapper.insertGeneration(
                goalId, sourceReportId, revision, triggerType, status,
                promptVersion, idempotencyKey, requestedAt
        );
        if (id == null) {
            throw new IllegalStateException("성장 액션 생성 작업 INSERT 결과에 ID가 없습니다.");
        }
        return id;
    }

    @Override
    public void supersedeOlder(long goalId, long revision) {
        mapper.supersedeOlder(goalId, revision);
    }

    @Override
    public void supersedeCompeting(long goalId, long generationId) {
        mapper.supersedeCompeting(goalId, generationId);
    }

    @Override
    public int markProcessing(long generationId, Instant startedAt) {
        return mapper.markGenerationProcessing(generationId, startedAt);
    }

    @Override
    public int markPending(long generationId, String errorCode) {
        return mapper.markGenerationPending(generationId, errorCode);
    }

    @Override
    public int markFailed(long generationId, Instant completedAt, String errorCode) {
        return mapper.markGenerationFailed(generationId, completedAt, errorCode);
    }

    @Override
    public int markCompleted(long generationId, String fingerprint, Instant completedAt) {
        return mapper.markGenerationCompleted(generationId, fingerprint, completedAt);
    }

    @Override
    public void replaceActions(
            long goalId, long generationId, List<GeneratedGrowthAction> actions
    ) {
        mapper.deleteActions(goalId);
        mapper.insertActions(goalId, generationId, actions);
    }
}

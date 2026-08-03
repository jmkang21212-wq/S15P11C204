package com.example.laive.growth.repository.mybatis;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionJobStatus;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.ai.RecentGrowthReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface GrowthMapper {
    Optional<GrowthGoal> findGoal(@Param("goalId") long goalId);
    List<GrowthSeriesPoint> findSeries(
            @Param("streamerId") long streamerId,
            @Param("periodType") GrowthPeriodType periodType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("platformCode") String platformCode
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

    Optional<GrowthActionGeneration> findGeneration(@Param("generationId") long generationId);
    Optional<GrowthActionGeneration> findGenerationForUpdate(
            @Param("generationId") long generationId
    );
    List<GrowthActionGeneration> findLatestGenerations(@Param("goalIds") List<Long> goalIds);
    Optional<GrowthActionGeneration> findRunningGeneration(@Param("goalId") long goalId);
    boolean hasEligibleReport(@Param("goalId") long goalId);
    List<RecentGrowthReport> findRecentReports(
            @Param("goalId") long goalId,
            @Param("limit") int limit
    );
    List<GrowthGoal> findActiveGoalsAffectedByReport(@Param("reportId") long reportId);
    Long insertGeneration(
            @Param("goalId") long goalId,
            @Param("sourceReportId") Long sourceReportId,
            @Param("revision") long revision,
            @Param("triggerType") GrowthActionTriggerType triggerType,
            @Param("status") GrowthActionJobStatus status,
            @Param("promptVersion") String promptVersion,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestedAt") Instant requestedAt
    );
    int supersedeOlder(@Param("goalId") long goalId, @Param("revision") long revision);
    int supersedeCompeting(
            @Param("goalId") long goalId,
            @Param("generationId") long generationId
    );
    int markGenerationProcessing(
            @Param("generationId") long generationId,
            @Param("startedAt") Instant startedAt
    );
    int markGenerationPending(
            @Param("generationId") long generationId,
            @Param("errorCode") String errorCode
    );
    int markGenerationFailed(
            @Param("generationId") long generationId,
            @Param("completedAt") Instant completedAt,
            @Param("errorCode") String errorCode
    );
    int markGenerationCompleted(
            @Param("generationId") long generationId,
            @Param("fingerprint") String fingerprint,
            @Param("completedAt") Instant completedAt
    );
    int deleteActions(@Param("goalId") long goalId);
    int insertActions(
            @Param("goalId") long goalId,
            @Param("generationId") long generationId,
            @Param("actions") List<GeneratedGrowthAction> actions
    );
}

package com.example.laive.growth.repository;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.ai.RecentGrowthReport;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionJobStatus;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.domain.GrowthGoal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GrowthActionRepository {
    Optional<GrowthGoal> findGoal(long goalId);
    Optional<GrowthActionGeneration> findGeneration(long generationId);
    Optional<GrowthActionGeneration> findGenerationForUpdate(long generationId);
    List<GrowthActionGeneration> findLatestGenerations(List<Long> goalIds);
    Optional<GrowthActionGeneration> findRunningGeneration(long goalId);
    boolean hasEligibleReport(long goalId);
    List<RecentGrowthReport> findRecentReports(long goalId, int limit);
    List<GrowthGoal> findActiveGoalsAffectedByReport(long reportId);

    long insertGeneration(
            long goalId,
            Long sourceReportId,
            long revision,
            GrowthActionTriggerType triggerType,
            GrowthActionJobStatus status,
            String promptVersion,
            String idempotencyKey,
            Instant requestedAt
    );

    void supersedeOlder(long goalId, long revision);
    void supersedeCompeting(long goalId, long generationId);
    int markProcessing(long generationId, Instant startedAt);
    int markPending(long generationId, String errorCode);
    int markFailed(long generationId, Instant completedAt, String errorCode);
    int markCompleted(long generationId, String fingerprint, Instant completedAt);
    void replaceActions(long goalId, long generationId, List<GeneratedGrowthAction> actions);
}

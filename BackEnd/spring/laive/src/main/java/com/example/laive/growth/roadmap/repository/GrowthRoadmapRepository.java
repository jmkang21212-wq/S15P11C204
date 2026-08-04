package com.example.laive.growth.roadmap.repository;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.roadmap.ai.AcceptedGoalSummary;
import com.example.laive.growth.roadmap.ai.PreviousSuggestionSummary;
import com.example.laive.growth.roadmap.ai.RecentBroadcastStatistics;
import com.example.laive.growth.roadmap.ai.RoadmapSessionReport;
import com.example.laive.growth.roadmap.ai.RoadmapTrendPoint;
import com.example.laive.growth.roadmap.ai.SuggestedGrowthGoal;
import com.example.laive.growth.roadmap.domain.GrowthGoalSuggestion;
import com.example.laive.growth.roadmap.domain.GrowthRoadmap;
import com.example.laive.growth.roadmap.domain.GrowthRoadmapGeneration;
import com.example.laive.growth.roadmap.domain.GrowthSuggestionAction;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationStatus;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationTrigger;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.domain.RoadmapStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GrowthRoadmapRepository {
    Optional<GrowthRoadmap> findRoadmap(long streamerId);

    Optional<GrowthRoadmap> findRoadmapForUpdate(long roadmapId);

    long insertRoadmap(long streamerId, String promptVersion);

    long countEligibleReports(long streamerId);

    Optional<Long> findStreamerIdByReport(long reportId);

    Optional<GrowthRoadmapGeneration> findGeneration(long generationId);

    Optional<GrowthRoadmapGeneration> findGenerationForUpdate(long generationId);

    Optional<GrowthRoadmapGeneration> findRunningGeneration(long roadmapId);

    long insertGeneration(
            long roadmapId,
            Long sourceReportId,
            RoadmapGenerationTrigger triggerType,
            RoadmapGenerationStatus status,
            RoadmapQuality quality,
            String promptVersion,
            String idempotencyKey,
            Instant requestedAt
    );

    void supersedeCompeting(long roadmapId, long generationId);

    int markGenerationProcessing(long generationId, Instant startedAt);

    int markGenerationPending(long generationId, String errorCode);

    int markGenerationCompleted(
            long generationId, String fingerprint, Instant completedAt
    );

    int markGenerationFailed(long generationId, Instant completedAt, String errorCode);

    void markRoadmapPending(
            long roadmapId, long generationId, Long sourceReportId,
            RoadmapQuality quality, String promptVersion
    );

    void markRoadmapWaiting(long roadmapId, String promptVersion);

    void markRoadmapStale(long roadmapId);

    void markRoadmapProcessing(long roadmapId);

    void markRoadmapReady(
            long roadmapId, long generationId, Long sourceReportId,
            RoadmapQuality quality, String promptVersion, Instant generatedAt
    );

    void markRoadmapFailed(long roadmapId, String errorCode);

    List<GrowthGoalSuggestion> findCurrentSuggestions(long roadmapId);

    List<GrowthSuggestionAction> findSuggestionActions(List<Long> suggestionIds);

    Optional<GrowthGoalSuggestion> findOwnedSuggestionForUpdate(
            long streamerId, long suggestionId
    );

    void supersedeSuggested(long roadmapId);

    long insertSuggestion(
            long roadmapId, long generationId, int displayOrder, SuggestedGrowthGoal suggestion
    );

    void insertSuggestionActions(
            long suggestionId, List<GeneratedGrowthAction> actions
    );

    int acceptSuggestion(long suggestionId, long goalId, Instant acceptedAt);

    int dismissSuggestion(long suggestionId, Instant dismissedAt);

    void copySuggestionActions(long suggestionId, long goalId);

    List<RoadmapTrendPoint> findWeeklyTrends(
            long streamerId, LocalDate from, LocalDate to
    );

    List<RoadmapSessionReport> findRecentReports(long streamerId, int limit);

    RecentBroadcastStatistics findRecentStatistics(long streamerId, Instant from);

    List<PreviousSuggestionSummary> findPreviousSuggestions(long streamerId, int limit);

    List<AcceptedGoalSummary> findAcceptedGoals(long streamerId);

    Optional<Instant> findLatestCompletedAt(long roadmapId);

    List<GrowthGoal> findActiveGoals(long streamerId);

    List<Long> findStreamersNeedingPromptVersion(
            String promptVersion, int limit
    );
}

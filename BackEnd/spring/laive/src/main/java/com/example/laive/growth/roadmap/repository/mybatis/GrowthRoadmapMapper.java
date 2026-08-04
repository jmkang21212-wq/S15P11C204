package com.example.laive.growth.roadmap.repository.mybatis;

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
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface GrowthRoadmapMapper {
    Optional<GrowthRoadmap> findRoadmap(@Param("streamerId") long streamerId);
    Optional<GrowthRoadmap> findRoadmapForUpdate(@Param("roadmapId") long roadmapId);
    Long insertRoadmap(@Param("streamerId") long streamerId,
                       @Param("promptVersion") String promptVersion);
    long countEligibleReports(@Param("streamerId") long streamerId);
    Optional<Long> findStreamerIdByReport(@Param("reportId") long reportId);
    Optional<GrowthRoadmapGeneration> findGeneration(@Param("generationId") long generationId);
    Optional<GrowthRoadmapGeneration> findGenerationForUpdate(
            @Param("generationId") long generationId);
    Optional<GrowthRoadmapGeneration> findRunningGeneration(@Param("roadmapId") long roadmapId);
    Long insertGeneration(
            @Param("roadmapId") long roadmapId,
            @Param("sourceReportId") Long sourceReportId,
            @Param("triggerType") RoadmapGenerationTrigger triggerType,
            @Param("status") RoadmapGenerationStatus status,
            @Param("quality") RoadmapQuality quality,
            @Param("promptVersion") String promptVersion,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestedAt") Instant requestedAt);
    int supersedeCompeting(@Param("roadmapId") long roadmapId,
                           @Param("generationId") long generationId);
    int markGenerationProcessing(@Param("generationId") long generationId,
                                 @Param("startedAt") Instant startedAt);
    int markGenerationPending(@Param("generationId") long generationId,
                              @Param("errorCode") String errorCode);
    int markGenerationCompleted(@Param("generationId") long generationId,
                                @Param("fingerprint") String fingerprint,
                                @Param("completedAt") Instant completedAt);
    int markGenerationFailed(@Param("generationId") long generationId,
                             @Param("completedAt") Instant completedAt,
                             @Param("errorCode") String errorCode);
    int markRoadmapPending(@Param("roadmapId") long roadmapId,
                           @Param("generationId") long generationId,
                           @Param("sourceReportId") Long sourceReportId,
                           @Param("quality") RoadmapQuality quality,
                           @Param("promptVersion") String promptVersion);
    int markRoadmapWaiting(@Param("roadmapId") long roadmapId,
                           @Param("promptVersion") String promptVersion);
    int markRoadmapStale(@Param("roadmapId") long roadmapId);
    int markRoadmapProcessing(@Param("roadmapId") long roadmapId);
    int markRoadmapReady(@Param("roadmapId") long roadmapId,
                         @Param("generationId") long generationId,
                         @Param("sourceReportId") Long sourceReportId,
                         @Param("quality") RoadmapQuality quality,
                         @Param("promptVersion") String promptVersion,
                         @Param("generatedAt") Instant generatedAt);
    int markRoadmapFailed(@Param("roadmapId") long roadmapId,
                          @Param("errorCode") String errorCode);
    List<GrowthGoalSuggestion> findCurrentSuggestions(@Param("roadmapId") long roadmapId);
    List<GrowthSuggestionAction> findSuggestionActions(
            @Param("suggestionIds") List<Long> suggestionIds);
    Optional<GrowthGoalSuggestion> findOwnedSuggestionForUpdate(
            @Param("streamerId") long streamerId,
            @Param("suggestionId") long suggestionId);
    int supersedeSuggested(@Param("roadmapId") long roadmapId);
    Long insertSuggestion(@Param("roadmapId") long roadmapId,
                          @Param("generationId") long generationId,
                          @Param("displayOrder") int displayOrder,
                          @Param("suggestion") SuggestedGrowthGoal suggestion);
    int insertSuggestionActions(@Param("suggestionId") long suggestionId,
                                @Param("actions") List<GeneratedGrowthAction> actions);
    int acceptSuggestion(@Param("suggestionId") long suggestionId,
                         @Param("goalId") long goalId,
                         @Param("acceptedAt") Instant acceptedAt);
    int dismissSuggestion(@Param("suggestionId") long suggestionId,
                          @Param("dismissedAt") Instant dismissedAt);
    int copySuggestionActions(@Param("suggestionId") long suggestionId,
                              @Param("goalId") long goalId);
    List<RoadmapTrendPoint> findWeeklyTrends(@Param("streamerId") long streamerId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);
    List<RoadmapSessionReport> findRecentReports(@Param("streamerId") long streamerId,
                                                 @Param("limit") int limit);
    RecentBroadcastStatistics findRecentStatistics(@Param("streamerId") long streamerId,
                                                    @Param("from") Instant from);
    List<PreviousSuggestionSummary> findPreviousSuggestions(
            @Param("streamerId") long streamerId, @Param("limit") int limit);
    List<AcceptedGoalSummary> findAcceptedGoals(@Param("streamerId") long streamerId);
    Optional<Instant> findLatestCompletedAt(@Param("roadmapId") long roadmapId);
    List<GrowthGoal> findActiveGoals(@Param("streamerId") long streamerId);
    List<Long> findStreamersNeedingPromptVersion(
            @Param("promptVersion") String promptVersion,
            @Param("limit") int limit);
}

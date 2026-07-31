package com.example.laive.growth.roadmap.repository.mybatis;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.roadmap.ai.*;
import com.example.laive.growth.roadmap.domain.*;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGrowthRoadmapRepository implements GrowthRoadmapRepository {
    private final GrowthRoadmapMapper mapper;

    public MyBatisGrowthRoadmapRepository(GrowthRoadmapMapper mapper) {
        this.mapper = mapper;
    }

    @Override public Optional<GrowthRoadmap> findRoadmap(long id) { return mapper.findRoadmap(id); }
    @Override public Optional<GrowthRoadmap> findRoadmapForUpdate(long id) { return mapper.findRoadmapForUpdate(id); }
    @Override public long insertRoadmap(long id, String version) { return required(mapper.insertRoadmap(id, version)); }
    @Override public long countEligibleReports(long id) { return mapper.countEligibleReports(id); }
    @Override public Optional<Long> findStreamerIdByReport(long id) { return mapper.findStreamerIdByReport(id); }
    @Override public Optional<GrowthRoadmapGeneration> findGeneration(long id) { return mapper.findGeneration(id); }
    @Override public Optional<GrowthRoadmapGeneration> findGenerationForUpdate(long id) { return mapper.findGenerationForUpdate(id); }
    @Override public Optional<GrowthRoadmapGeneration> findRunningGeneration(long id) { return mapper.findRunningGeneration(id); }
    @Override public long insertGeneration(long roadmapId, Long reportId, RoadmapGenerationTrigger trigger, RoadmapGenerationStatus status, RoadmapQuality quality, String version, String key, Instant at) {
        return required(mapper.insertGeneration(roadmapId, reportId, trigger, status, quality, version, key, at));
    }
    @Override public void supersedeCompeting(long roadmapId, long generationId) { mapper.supersedeCompeting(roadmapId, generationId); }
    @Override public int markGenerationProcessing(long id, Instant at) { return mapper.markGenerationProcessing(id, at); }
    @Override public int markGenerationPending(long id, String error) { return mapper.markGenerationPending(id, error); }
    @Override public int markGenerationCompleted(long id, String fingerprint, Instant at) { return mapper.markGenerationCompleted(id, fingerprint, at); }
    @Override public int markGenerationFailed(long id, Instant at, String error) { return mapper.markGenerationFailed(id, at, error); }
    @Override public void markRoadmapPending(long id, long generationId, Long reportId, RoadmapQuality quality, String version) { mapper.markRoadmapPending(id, generationId, reportId, quality, version); }
    @Override public void markRoadmapWaiting(long id, String version) { mapper.markRoadmapWaiting(id, version); }
    @Override public void markRoadmapStale(long id) { mapper.markRoadmapStale(id); }
    @Override public void markRoadmapProcessing(long id) { mapper.markRoadmapProcessing(id); }
    @Override public void markRoadmapReady(long id, long generationId, Long reportId, RoadmapQuality quality, String version, Instant at) { mapper.markRoadmapReady(id, generationId, reportId, quality, version, at); }
    @Override public void markRoadmapFailed(long id, String error) { mapper.markRoadmapFailed(id, error); }
    @Override public List<GrowthGoalSuggestion> findCurrentSuggestions(long id) { return mapper.findCurrentSuggestions(id); }
    @Override public List<GrowthSuggestionAction> findSuggestionActions(List<Long> ids) { return ids.isEmpty() ? List.of() : mapper.findSuggestionActions(ids); }
    @Override public Optional<GrowthGoalSuggestion> findOwnedSuggestionForUpdate(long streamerId, long suggestionId) { return mapper.findOwnedSuggestionForUpdate(streamerId, suggestionId); }
    @Override public void supersedeSuggested(long id) { mapper.supersedeSuggested(id); }
    @Override public long insertSuggestion(long roadmapId, long generationId, int order, SuggestedGrowthGoal suggestion) { return required(mapper.insertSuggestion(roadmapId, generationId, order, suggestion)); }
    @Override public void insertSuggestionActions(long id, List<GeneratedGrowthAction> actions) { mapper.insertSuggestionActions(id, actions); }
    @Override public int acceptSuggestion(long id, long goalId, Instant at) { return mapper.acceptSuggestion(id, goalId, at); }
    @Override public int dismissSuggestion(long id, Instant at) { return mapper.dismissSuggestion(id, at); }
    @Override public void copySuggestionActions(long suggestionId, long goalId) { mapper.copySuggestionActions(suggestionId, goalId); }
    @Override public List<RoadmapTrendPoint> findWeeklyTrends(long id, LocalDate from, LocalDate to) { return mapper.findWeeklyTrends(id, from, to); }
    @Override public List<RoadmapSessionReport> findRecentReports(long id, int limit) { return mapper.findRecentReports(id, limit); }
    @Override public RecentBroadcastStatistics findRecentStatistics(long id, Instant from) { return mapper.findRecentStatistics(id, from); }
    @Override public List<PreviousSuggestionSummary> findPreviousSuggestions(long id, int limit) { return mapper.findPreviousSuggestions(id, limit); }
    @Override public List<AcceptedGoalSummary> findAcceptedGoals(long id) { return mapper.findAcceptedGoals(id); }
    @Override public Optional<Instant> findLatestCompletedAt(long id) { return mapper.findLatestCompletedAt(id); }
    @Override public List<GrowthGoal> findActiveGoals(long id) { return mapper.findActiveGoals(id); }
    @Override public List<Long> findStreamersNeedingPromptVersion(String version, int limit) { return mapper.findStreamersNeedingPromptVersion(version, limit); }

    private long required(Long value) {
        if (value == null) throw new IllegalStateException("Roadmap INSERT did not return an id.");
        return value;
    }
}

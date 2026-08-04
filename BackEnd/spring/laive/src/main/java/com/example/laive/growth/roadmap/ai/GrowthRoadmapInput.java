package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record GrowthRoadmapInput(
        long streamerId,
        Optional<PortfolioRoadmapSnapshot> portfolio,
        List<RoadmapTrendPoint> weeklyTrends,
        List<RoadmapSessionReport> recentReports,
        RecentBroadcastStatistics statistics,
        List<PreviousSuggestionSummary> previousSuggestions,
        List<AcceptedGoalSummary> acceptedGoals,
        Map<GrowthMetricType, BigDecimal> currentValues,
        RoadmapQuality quality,
        String promptVersion,
        String inputFingerprint
) {
    public GrowthRoadmapInput {
        portfolio = portfolio == null ? Optional.empty() : portfolio;
        weeklyTrends = List.copyOf(weeklyTrends);
        recentReports = List.copyOf(recentReports);
        previousSuggestions = List.copyOf(previousSuggestions);
        acceptedGoals = List.copyOf(acceptedGoals);
        currentValues = Map.copyOf(currentValues);
    }
}

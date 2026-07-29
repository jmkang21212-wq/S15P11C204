package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthGoalView;
import com.example.laive.growth.domain.GrowthMath;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthGoalQueryRepository;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GrowthGoalQueryService {
    private final GrowthGoalQueryRepository goalRepository;
    private final GrowthReportQueryRepository reportRepository;

    public GrowthGoalQueryService(
            GrowthGoalQueryRepository goalRepository,
            GrowthReportQueryRepository reportRepository
    ) {
        this.goalRepository = goalRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public List<GrowthGoalView> getGoals(long streamerId, GrowthGoalStatus status) {
        List<GrowthGoal> goals = goalRepository.findGoals(streamerId, status);
        return assemble(streamerId, goals);
    }

    @Transactional(readOnly = true)
    public GrowthGoalView getGoal(long streamerId, long goalId) {
        GrowthGoal goal = goalRepository.findOwned(streamerId, goalId)
                .orElseThrow(GrowthException::goalNotFound);
        return assemble(streamerId, List.of(goal)).getFirst();
    }

    private List<GrowthGoalView> assemble(long streamerId, List<GrowthGoal> goals) {
        if (goals.isEmpty()) {
            return List.of();
        }
        List<Long> ids = goals.stream().map(GrowthGoal::id).toList();
        Map<Long, GrowthGoalMetrics> metrics = reportRepository
                .findGoalMetrics(streamerId, ids)
                .stream()
                .collect(Collectors.toMap(GrowthGoalMetrics::growthGoalId, row -> row));
        Map<Long, List<GrowthAction>> actions = goalRepository.findActions(ids)
                .stream()
                .collect(Collectors.groupingBy(GrowthAction::growthGoalId));
        return goals.stream().map(goal -> {
            GrowthGoalMetrics row = metrics.get(goal.id());
            var current = row == null || row.valueFor(goal.metricType()) == null
                    ? java.math.BigDecimal.ZERO
                    : row.valueFor(goal.metricType());
            return new GrowthGoalView(
                    goal,
                    current,
                    GrowthMath.progress(current, goal.targetValue()),
                    actions.getOrDefault(goal.id(), List.of())
            );
        }).toList();
    }
}

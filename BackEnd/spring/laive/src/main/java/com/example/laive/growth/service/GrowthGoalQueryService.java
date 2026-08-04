package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionGenerationStatus;
import com.example.laive.growth.domain.GrowthActionGenerationView;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthGoalView;
import com.example.laive.growth.domain.GrowthMath;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthGoalQueryRepository;
import com.example.laive.growth.repository.GrowthActionRepository;
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
    private final GrowthActionRepository actionRepository;

    public GrowthGoalQueryService(
            GrowthGoalQueryRepository goalRepository,
            GrowthReportQueryRepository reportRepository,
            GrowthActionRepository actionRepository
    ) {
        this.goalRepository = goalRepository;
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
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
        Map<Long, GrowthActionGeneration> generations = actionRepository.findLatestGenerations(ids)
                .stream()
                .collect(Collectors.toMap(GrowthActionGeneration::growthGoalId, row -> row));
        return goals.stream().map(goal -> {
            GrowthGoalMetrics row = metrics.get(goal.id());
            var current = row == null || row.valueFor(goal.metricType()) == null
                    ? java.math.BigDecimal.ZERO
                    : row.valueFor(goal.metricType());
            List<GrowthAction> goalActions = actions.getOrDefault(goal.id(), List.of());
            return new GrowthGoalView(
                    goal,
                    current,
                    GrowthMath.progress(current, goal.targetValue()),
                    goalActions,
                    generationView(generations.get(goal.id()), goalActions)
            );
        }).toList();
    }

    private GrowthActionGenerationView generationView(
            GrowthActionGeneration generation,
            List<GrowthAction> actions
    ) {
        if (generation == null) {
            return GrowthActionGenerationView.notRequested();
        }
        GrowthActionGenerationStatus status = switch (generation.status()) {
            case WAITING_FOR_DATA -> GrowthActionGenerationStatus.WAITING_FOR_DATA;
            case PENDING -> GrowthActionGenerationStatus.PENDING;
            case PROCESSING -> GrowthActionGenerationStatus.PROCESSING;
            case COMPLETED -> GrowthActionGenerationStatus.COMPLETED;
            case FAILED -> GrowthActionGenerationStatus.FAILED;
            case SUPERSEDED -> GrowthActionGenerationStatus.NOT_REQUESTED;
        };
        boolean stale = !actions.isEmpty() && (
                generation.status() != com.example.laive.growth.domain.GrowthActionJobStatus.COMPLETED
                        || actions.stream().anyMatch(action ->
                        action.generationId() == null || action.generationId() != generation.id())
        );
        return new GrowthActionGenerationView(
                status,
                stale,
                generation.completedAt(),
                generation.sourceReportId(),
                generation.promptVersion(),
                generation.errorCode()
        );
    }
}

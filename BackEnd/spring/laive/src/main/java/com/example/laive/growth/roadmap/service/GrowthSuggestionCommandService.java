package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthGoalCommandRepository;
import com.example.laive.growth.roadmap.domain.SuggestionStatus;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import com.example.laive.growth.service.GrowthActionTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class GrowthSuggestionCommandService {
    private final GrowthRoadmapRepository roadmapRepository;
    private final GrowthGoalCommandRepository goalRepository;
    private final GrowthActionTransactionService actionTransactions;
    private final Clock clock;

    public GrowthSuggestionCommandService(
            GrowthRoadmapRepository roadmapRepository,
            GrowthGoalCommandRepository goalRepository,
            GrowthActionTransactionService actionTransactions,
            Clock clock
    ) {
        this.roadmapRepository = roadmapRepository;
        this.goalRepository = goalRepository;
        this.actionTransactions = actionTransactions;
        this.clock = clock;
    }

    @Transactional
    public AcceptedSuggestionResult accept(long streamerId, long suggestionId) {
        var suggestion = roadmapRepository
                .findOwnedSuggestionForUpdate(streamerId, suggestionId)
                .orElseThrow(GrowthException::roadmapNotFound);
        if (suggestion.status() == SuggestionStatus.ACCEPTED
                && suggestion.acceptedGoalId() != null) {
            return new AcceptedSuggestionResult(suggestion.acceptedGoalId(), false);
        }
        if (suggestion.status() != SuggestionStatus.SUGGESTED) {
            throw GrowthException.roadmapSuggestionConflict();
        }
        GrowthGoal goal = GrowthGoal.create(
                streamerId, suggestion.metricType(), suggestion.targetValue(),
                suggestion.unit(), suggestion.periodStart(), suggestion.periodEnd()
        );
        goalRepository.lockScope(streamerId, goal.metricType());
        if (goalRepository.existsOverlappingActive(
                streamerId, goal.metricType(), goal.periodStart(), goal.periodEnd(), null
        )) {
            throw GrowthException.stateConflict();
        }
        long goalId = goalRepository.insert(goal);
        roadmapRepository.copySuggestionActions(suggestionId, goalId);
        if (roadmapRepository.acceptSuggestion(
                suggestionId, goalId, clock.instant()
        ) != 1) {
            throw GrowthException.roadmapSuggestionConflict();
        }
        GrowthGoal saved = goalRepository.findOwnedForUpdate(streamerId, goalId)
                .orElseThrow();
        actionTransactions.request(
                saved, null, GrowthActionTriggerType.GOAL_CREATED, null
        );
        return new AcceptedSuggestionResult(goalId, true);
    }

    @Transactional
    public void dismiss(long streamerId, long suggestionId) {
        var suggestion = roadmapRepository
                .findOwnedSuggestionForUpdate(streamerId, suggestionId)
                .orElseThrow(GrowthException::roadmapNotFound);
        if (suggestion.status() == SuggestionStatus.DISMISSED) return;
        if (suggestion.status() != SuggestionStatus.SUGGESTED) {
            throw GrowthException.roadmapSuggestionConflict();
        }
        if (roadmapRepository.dismissSuggestion(
                suggestionId, clock.instant()
        ) != 1) {
            throw GrowthException.roadmapSuggestionConflict();
        }
    }
}

package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthGoalCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthGoalCommandService {
    private final GrowthGoalCommandRepository repository;
    private final GrowthActionTransactionService actionTransactions;

    public GrowthGoalCommandService(
            GrowthGoalCommandRepository repository,
            GrowthActionTransactionService actionTransactions
    ) {
        this.repository = repository;
        this.actionTransactions = actionTransactions;
    }

    @Transactional
    public long create(long streamerId, CreateGrowthGoalCommand command) {
        GrowthGoal goal = GrowthGoal.create(
                streamerId,
                command.metricType(),
                command.targetValue(),
                command.unit(),
                command.periodStart(),
                command.periodEnd()
        );
        repository.lockScope(streamerId, goal.metricType());
        ensureNoOverlap(goal, null);
        long id = repository.insert(goal);
        GrowthGoal saved = repository.findOwnedForUpdate(streamerId, id).orElseThrow();
        actionTransactions.request(saved, null, GrowthActionTriggerType.GOAL_CREATED, null);
        return id;
    }

    @Transactional
    public void update(long streamerId, long goalId, UpdateGrowthGoalCommand command) {
        if (command.isEmpty()) {
            throw GrowthException.invalidGoal();
        }
        GrowthGoal current = repository.findOwnedForUpdate(streamerId, goalId)
                .orElseThrow(GrowthException::goalNotFound);
        GrowthGoal updated = current.update(
                command.metricType(),
                command.targetValue(),
                command.unit(),
                command.periodStart(),
                command.periodEnd(),
                command.status()
        );
        repository.lockScope(streamerId, updated.metricType());
        if (updated.status() == GrowthGoalStatus.ACTIVE) {
            ensureNoOverlap(updated, goalId);
        }
        if (repository.update(updated) != 1) {
            throw GrowthException.stateConflict();
        }
        if (updated.status() == GrowthGoalStatus.ACTIVE
                && (current.status() != GrowthGoalStatus.ACTIVE
                || materiallyChanged(current, updated))) {
            actionTransactions.request(
                    updated, null, GrowthActionTriggerType.GOAL_UPDATED, null
            );
        } else if (updated.status() != GrowthGoalStatus.ACTIVE) {
            actionTransactions.supersedeBeforeRevision(updated);
        }
    }

    private void ensureNoOverlap(GrowthGoal goal, Long excludedGoalId) {
        if (repository.existsOverlappingActive(
                goal.streamerId(),
                goal.metricType(),
                goal.periodStart(),
                goal.periodEnd(),
                excludedGoalId
        )) {
            throw GrowthException.stateConflict();
        }
    }

    private boolean materiallyChanged(GrowthGoal current, GrowthGoal updated) {
        return current.metricType() != updated.metricType()
                || current.targetValue().compareTo(updated.targetValue()) != 0
                || current.unit() != updated.unit()
                || !current.periodStart().equals(updated.periodStart())
                || !current.periodEnd().equals(updated.periodEnd());
    }
}

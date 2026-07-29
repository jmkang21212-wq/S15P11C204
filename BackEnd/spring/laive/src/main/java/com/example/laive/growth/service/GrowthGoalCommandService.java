package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthGoalCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrowthGoalCommandService {
    private final GrowthGoalCommandRepository repository;

    public GrowthGoalCommandService(GrowthGoalCommandRepository repository) {
        this.repository = repository;
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
        return repository.insert(goal);
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
}

package com.example.laive.growth.service;

import com.example.laive.growth.config.GrowthAiProperties;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthActionRepository;
import com.example.laive.growth.repository.GrowthGoalQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GrowthActionCommandService {
    private final GrowthGoalQueryRepository goalRepository;
    private final GrowthActionRepository actionRepository;
    private final GrowthActionTransactionService transactions;
    private final GrowthAiProperties properties;

    public GrowthActionCommandService(
            GrowthGoalQueryRepository goalRepository,
            GrowthActionRepository actionRepository,
            GrowthActionTransactionService transactions,
            GrowthAiProperties properties
    ) {
        this.goalRepository = goalRepository;
        this.actionRepository = actionRepository;
        this.transactions = transactions;
        this.properties = properties;
    }

    @Transactional
    public GrowthActionGeneration regenerate(long streamerId, long goalId) {
        var goal = goalRepository.findOwned(streamerId, goalId)
                .orElseThrow(GrowthException::goalNotFound);
        if (goal.status() != GrowthGoalStatus.ACTIVE) {
            throw GrowthException.actionStateConflict();
        }
        if (!properties.enabled()) {
            throw GrowthException.actionGenerationDisabled();
        }
        var running = actionRepository.findRunningGeneration(goalId);
        if (running.isPresent()) {
            return running.get();
        }
        if (!actionRepository.hasEligibleReport(goalId)) {
            throw GrowthException.insufficientActionData();
        }
        return transactions.request(
                goal, null, GrowthActionTriggerType.MANUAL, UUID.randomUUID().toString()
        );
    }
}

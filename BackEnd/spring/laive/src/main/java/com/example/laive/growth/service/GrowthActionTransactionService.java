package com.example.laive.growth.service;

import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.growth.ai.GrowthActionInput;
import com.example.laive.growth.ai.GrowthActionResult;
import com.example.laive.growth.config.GrowthAiProperties;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionJobStatus;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.repository.GrowthActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class GrowthActionTransactionService {
    private final GrowthActionRepository repository;
    private final BroadcastOutboxRepository outboxRepository;
    private final OutboxEventFactory eventFactory;
    private final GrowthAiProperties properties;
    private final Clock clock;

    public GrowthActionTransactionService(
            GrowthActionRepository repository,
            BroadcastOutboxRepository outboxRepository,
            OutboxEventFactory eventFactory,
            GrowthAiProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.eventFactory = eventFactory;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public GrowthActionGeneration request(
            GrowthGoal goal,
            Long sourceReportId,
            GrowthActionTriggerType triggerType,
            String manualKey
    ) {
        repository.supersedeOlder(goal.id(), goal.revision());
        boolean hasData = repository.hasEligibleReport(goal.id());
        GrowthActionJobStatus status = hasData && properties.enabled()
                ? GrowthActionJobStatus.PENDING
                : GrowthActionJobStatus.WAITING_FOR_DATA;
        String key = idempotencyKey(goal, sourceReportId, triggerType, manualKey);
        Instant now = clock.instant();
        long id = repository.insertGeneration(
                goal.id(), sourceReportId, goal.revision(), triggerType, status,
                properties.promptVersion(), key, now
        );
        repository.supersedeCompeting(goal.id(), id);
        if (status == GrowthActionJobStatus.PENDING) {
            var event = eventFactory.growthActionGenerationRequested(
                    id, goal.id(), goal.revision(), sourceReportId,
                    properties.promptVersion(), key, now
            );
            outboxRepository.save(
                    event.eventId(), event.aggregateId(), event.eventType(),
                    event.deduplicationKey(), event.payload(), event.availableAt()
            );
        }
        return repository.findGeneration(id).orElseThrow();
    }

    @Transactional
    public Optional<GrowthActionGeneration> begin(long generationId) {
        var generation = repository.findGenerationForUpdate(generationId).orElse(null);
        if (generation == null || generation.status() != GrowthActionJobStatus.PENDING) {
            return Optional.empty();
        }
        GrowthGoal goal = repository.findGoal(generation.growthGoalId()).orElse(null);
        if (goal == null || goal.status() != GrowthGoalStatus.ACTIVE
                || goal.revision() != generation.goalRevision()) {
            repository.supersedeOlder(generation.growthGoalId(), generation.goalRevision() + 1);
            return Optional.empty();
        }
        if (repository.markProcessing(generationId, clock.instant()) != 1) {
            return Optional.empty();
        }
        return repository.findGeneration(generationId);
    }

    @Transactional
    public void complete(
            GrowthActionGeneration generation,
            GrowthActionInput input,
            GrowthActionResult result
    ) {
        var locked = repository.findGenerationForUpdate(generation.id()).orElseThrow();
        GrowthGoal goal = repository.findGoal(locked.growthGoalId()).orElseThrow();
        if (locked.status() != GrowthActionJobStatus.PROCESSING
                || goal.status() != GrowthGoalStatus.ACTIVE
                || goal.revision() != locked.goalRevision()) {
            repository.supersedeOlder(goal.id(), goal.revision());
            return;
        }
        repository.replaceActions(goal.id(), locked.id(), result.actions());
        repository.markCompleted(locked.id(), input.inputFingerprint(), clock.instant());
    }

    @Transactional
    public void retry(long generationId, String errorCode) {
        repository.markPending(generationId, errorCode);
    }

    @Transactional
    public void fail(long generationId, String errorCode) {
        repository.markFailed(generationId, clock.instant(), errorCode);
    }

    @Transactional
    public void requestForReport(long reportId) {
        for (GrowthGoal goal : repository.findActiveGoalsAffectedByReport(reportId)) {
            request(goal, reportId, GrowthActionTriggerType.REPORT_COMPLETED, null);
        }
    }

    @Transactional
    public void supersedeBeforeRevision(GrowthGoal goal) {
        repository.supersedeOlder(goal.id(), goal.revision());
    }

    private String idempotencyKey(
            GrowthGoal goal,
            Long reportId,
            GrowthActionTriggerType triggerType,
            String manualKey
    ) {
        String source = switch (triggerType) {
            case MANUAL -> "manual:" + (manualKey == null ? UUID.randomUUID() : manualKey);
            case REPORT_COMPLETED -> "report:" + reportId;
            default -> "goal-change";
        };
        return "growth-action:" + goal.id() + ":revision:" + goal.revision()
                + ":" + source + ":prompt:" + properties.promptVersion();
    }
}

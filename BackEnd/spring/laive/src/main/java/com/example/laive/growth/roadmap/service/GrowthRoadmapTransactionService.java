package com.example.laive.growth.roadmap.service;

import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.growth.roadmap.ai.GrowthRoadmapInput;
import com.example.laive.growth.roadmap.ai.GrowthRoadmapResult;
import com.example.laive.growth.roadmap.config.GrowthRoadmapAiProperties;
import com.example.laive.growth.roadmap.domain.*;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class GrowthRoadmapTransactionService {
    private final GrowthRoadmapRepository repository;
    private final BroadcastOutboxRepository outboxRepository;
    private final OutboxEventFactory eventFactory;
    private final GrowthRoadmapAiProperties properties;
    private final Clock clock;

    public GrowthRoadmapTransactionService(
            GrowthRoadmapRepository repository,
            BroadcastOutboxRepository outboxRepository,
            OutboxEventFactory eventFactory,
            GrowthRoadmapAiProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.eventFactory = eventFactory;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<GrowthRoadmapGeneration> requestForReport(long reportId) {
        Optional<Long> owner = repository.findStreamerIdByReport(reportId);
        if (owner.isEmpty()) return Optional.empty();
        long streamerId = owner.get();
        GrowthRoadmap roadmap = getOrCreateLocked(streamerId);
        long count = repository.countEligibleReports(streamerId);
        if (!properties.enabled()) {
            if (roadmap.currentGenerationId() != null) repository.markRoadmapStale(roadmap.id());
            return Optional.empty();
        }
        if (count == 1) {
            return Optional.of(request(
                    roadmap, reportId, RoadmapGenerationTrigger.FIRST_REPORT,
                    RoadmapQuality.INITIAL, "report:" + reportId, clock.instant()
            ));
        }
        if (count == 2) {
            repository.markRoadmapStale(roadmap.id());
            return Optional.empty();
        }
        if (count == 3) {
            return Optional.of(request(
                    roadmap, reportId, RoadmapGenerationTrigger.THIRD_REPORT,
                    RoadmapQuality.ESTABLISHED, "report:" + reportId, clock.instant()
            ));
        }
        Instant latest = repository.findLatestCompletedAt(roadmap.id()).orElse(Instant.EPOCH);
        if (latest.plus(properties.refreshCooldown()).isAfter(clock.instant())) {
            repository.markRoadmapStale(roadmap.id());
            return Optional.empty();
        }
        return Optional.of(request(
                roadmap, reportId, RoadmapGenerationTrigger.REPORT_COMPLETED,
                RoadmapQuality.ESTABLISHED, "report:" + reportId, clock.instant()
        ));
    }

    @Transactional
    public GrowthRoadmapGeneration requestManual(long streamerId) {
        if (!properties.enabled()) {
            throw com.example.laive.growth.exception.GrowthException
                    .roadmapGenerationDisabled();
        }
        GrowthRoadmap roadmap = getOrCreateLocked(streamerId);
        long count = repository.countEligibleReports(streamerId);
        if (count == 0) {
            repository.markRoadmapWaiting(roadmap.id(), properties.promptVersion());
            throw com.example.laive.growth.exception.GrowthException
                    .insufficientRoadmapData();
        }
        var running = repository.findRunningGeneration(roadmap.id());
        if (running.isPresent()) return running.get();
        return request(
                roadmap, null, RoadmapGenerationTrigger.MANUAL,
                RoadmapQuality.fromReportCount(count),
                "manual:" + UUID.randomUUID(), clock.instant()
        );
    }

    @Transactional
    public void requestForPortfolio(long streamerId) {
        GrowthRoadmap roadmap = getOrCreateLocked(streamerId);
        long count = repository.countEligibleReports(streamerId);
        repository.markRoadmapStale(roadmap.id());
        if (count == 0 || !properties.enabled()) return;
        request(
                roadmap, null, RoadmapGenerationTrigger.PORTFOLIO_UPDATED,
                RoadmapQuality.fromReportCount(count),
                "portfolio:" + clock.instant().toEpochMilli(),
                clock.instant().plusSeconds(600)
        );
    }

    @Transactional
    public void requestForPromptVersion(long streamerId) {
        if (!properties.enabled()) return;
        GrowthRoadmap roadmap = getOrCreateLocked(streamerId);
        long count = repository.countEligibleReports(streamerId);
        if (count == 0) return;
        request(
                roadmap, null, RoadmapGenerationTrigger.PROMPT_VERSION_CHANGED,
                RoadmapQuality.fromReportCount(count),
                "prompt-change:" + properties.promptVersion(), clock.instant()
        );
    }

    @Transactional
    public Optional<GrowthRoadmapGeneration> begin(long generationId) {
        var generation = repository.findGenerationForUpdate(generationId).orElse(null);
        if (generation == null || generation.status() != RoadmapGenerationStatus.PENDING) {
            return Optional.empty();
        }
        if (repository.markGenerationProcessing(generationId, clock.instant()) != 1) {
            return Optional.empty();
        }
        repository.markRoadmapProcessing(generation.roadmapId());
        return repository.findGeneration(generationId);
    }

    @Transactional
    public void complete(
            GrowthRoadmapGeneration generation,
            GrowthRoadmapInput input,
            GrowthRoadmapResult result
    ) {
        var locked = repository.findGenerationForUpdate(generation.id()).orElseThrow();
        repository.findRoadmapForUpdate(locked.roadmapId()).orElseThrow();
        if (locked.status() != RoadmapGenerationStatus.PROCESSING) return;
        repository.supersedeSuggested(locked.roadmapId());
        for (int i = 0; i < result.goals().size(); i++) {
            var suggestion = result.goals().get(i);
            long suggestionId = repository.insertSuggestion(
                    locked.roadmapId(), locked.id(), i + 1, suggestion
            );
            repository.insertSuggestionActions(suggestionId, suggestion.actions());
        }
        Instant now = clock.instant();
        repository.markGenerationCompleted(locked.id(), input.inputFingerprint(), now);
        repository.markRoadmapReady(
                locked.roadmapId(), locked.id(), locked.sourceReportId(),
                locked.quality(), locked.promptVersion(), now
        );
    }

    @Transactional
    public void retry(long generationId, String errorCode) {
        repository.findGeneration(generationId).ifPresent(generation -> {
            if (repository.markGenerationPending(generationId, errorCode) == 1) {
                repository.markRoadmapPending(
                        generation.roadmapId(), generation.id(), generation.sourceReportId(),
                        generation.quality(), generation.promptVersion()
                );
            }
        });
    }

    @Transactional
    public void fail(long generationId, String errorCode) {
        repository.findGeneration(generationId).ifPresent(generation -> {
            if (repository.markGenerationFailed(
                    generationId, clock.instant(), errorCode
            ) == 1) {
                repository.markRoadmapFailed(generation.roadmapId(), errorCode);
            }
        });
    }

    private GrowthRoadmapGeneration request(
            GrowthRoadmap roadmap,
            Long sourceReportId,
            RoadmapGenerationTrigger trigger,
            RoadmapQuality quality,
            String sourceKey,
            Instant availableAt
    ) {
        Instant now = clock.instant();
        String key = "growth-roadmap:" + roadmap.streamerId() + ":" + sourceKey
                + ":prompt:" + properties.promptVersion();
        long generationId = repository.insertGeneration(
                roadmap.id(), sourceReportId, trigger, RoadmapGenerationStatus.PENDING,
                quality, properties.promptVersion(), key, now
        );
        GrowthRoadmapGeneration generation =
                repository.findGeneration(generationId).orElseThrow();
        if (generation.status() != RoadmapGenerationStatus.PENDING) return generation;
        repository.supersedeCompeting(roadmap.id(), generationId);
        repository.markRoadmapPending(
                roadmap.id(), generationId, sourceReportId, quality,
                properties.promptVersion()
        );
        var event = eventFactory.growthRoadmapGenerationRequested(
                generationId, roadmap.id(), roadmap.streamerId(), sourceReportId,
                trigger, quality, properties.promptVersion(), key, now, availableAt
        );
        outboxRepository.save(
                event.eventId(), event.aggregateId(), event.eventType(),
                event.deduplicationKey(), event.payload(), event.availableAt()
        );
        return generation;
    }

    private GrowthRoadmap getOrCreateLocked(long streamerId) {
        GrowthRoadmap roadmap = repository.findRoadmap(streamerId).orElseGet(() -> {
            long id = repository.insertRoadmap(streamerId, properties.promptVersion());
            return new GrowthRoadmap(
                    id, streamerId, RoadmapStatus.NOT_REQUESTED, null,
                    null, null, properties.promptVersion(), false, null, null
            );
        });
        return repository.findRoadmapForUpdate(roadmap.id()).orElseThrow();
    }
}

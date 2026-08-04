package com.example.laive.growth.roadmap.service;

import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.growth.roadmap.config.GrowthRoadmapAiProperties;
import com.example.laive.growth.roadmap.domain.GrowthRoadmapGeneration;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationStatus;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationTrigger;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthRoadmapTransactionServiceTest {
    private GrowthRoadmapRepository repository;
    private GrowthRoadmapTransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(GrowthRoadmapRepository.class);
        service = new GrowthRoadmapTransactionService(
                repository,
                mock(BroadcastOutboxRepository.class),
                mock(OutboxEventFactory.class),
                new GrowthRoadmapAiProperties(
                        true, "growth-roadmap-v1", 24_000,
                        Duration.ofSeconds(30), Duration.ofDays(7)
                ),
                Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void supersededRetryDoesNotOverwriteCurrentRoadmapState() {
        var generation = generation(RoadmapGenerationStatus.SUPERSEDED);
        when(repository.findGeneration(generation.id()))
                .thenReturn(Optional.of(generation));
        when(repository.markGenerationPending(generation.id(), "timeout"))
                .thenReturn(0);

        service.retry(generation.id(), "timeout");

        verify(repository, never()).markRoadmapPending(
                generation.roadmapId(), generation.id(), generation.sourceReportId(),
                generation.quality(), generation.promptVersion()
        );
    }

    @Test
    void supersededDeadEventDoesNotMarkCurrentRoadmapFailed() {
        var generation = generation(RoadmapGenerationStatus.SUPERSEDED);
        when(repository.findGeneration(generation.id()))
                .thenReturn(Optional.of(generation));
        when(repository.markGenerationFailed(
                generation.id(), Instant.parse("2026-08-01T00:00:00Z"), "OUTBOX_DEAD"
        )).thenReturn(0);

        service.fail(generation.id(), "OUTBOX_DEAD");

        verify(repository, never()).markRoadmapFailed(
                generation.roadmapId(), "OUTBOX_DEAD"
        );
    }

    private GrowthRoadmapGeneration generation(RoadmapGenerationStatus status) {
        return new GrowthRoadmapGeneration(
                31L, 21L, 11L, RoadmapGenerationTrigger.REPORT_COMPLETED,
                status, RoadmapQuality.ESTABLISHED, "growth-roadmap-v1",
                "idempotency-key", null, Instant.parse("2026-08-01T00:00:00Z"),
                null, null, null
        );
    }
}

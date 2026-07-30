package com.example.laive.report.service;

import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.report.repository.SessionReportGenerationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportGenerationTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T14:00:00Z");
    private final SessionReportGenerationRepository repository =
            mock(SessionReportGenerationRepository.class);
    private final BroadcastOutboxRepository outbox = mock(BroadcastOutboxRepository.class);
    private final ReportGenerationTransactionService service =
            new ReportGenerationTransactionService(
                    repository, outbox, new OutboxEventFactory(new ObjectMapper().findAndRegisterModules()),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void createsOneOutboxEventPerTrigger() {
        when(repository.markTriggersPending(10)).thenReturn(List.of(1L, 2L));

        service.start(10, 20);

        verify(outbox).save(any(), eq(10L),
                eq(BroadcastOutboxEventType.TRIGGER_ANALYSIS_REQUESTED),
                eq("report:20:trigger:1"), any(), eq(NOW));
        verify(outbox).save(any(), eq(10L),
                eq(BroadcastOutboxEventType.TRIGGER_ANALYSIS_REQUESTED),
                eq("report:20:trigger:2"), any(), eq(NOW));
    }

    @Test
    void zeroTriggerSchedulesFinalization() {
        when(repository.markTriggersPending(10)).thenReturn(List.of());
        when(repository.allTriggersFinished(10)).thenReturn(true);

        service.start(10, 20);

        verify(outbox).save(any(), eq(10L),
                eq(BroadcastOutboxEventType.REPORT_FINALIZATION_REQUESTED),
                eq("report:20:finalize"), any(), eq(NOW));
    }

    @Test
    void retryDoesNotFinalizeWhileOldTriggerJobsArePending() {
        when(repository.markTriggersPending(10)).thenReturn(List.of());
        when(repository.allTriggersFinished(10)).thenReturn(false);

        service.start(10, 20);

        verify(outbox, never()).save(any(), any(Long.class), any(), any(), any(), any());
    }
}

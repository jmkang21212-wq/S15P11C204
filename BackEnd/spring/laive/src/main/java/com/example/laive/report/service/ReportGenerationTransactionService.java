package com.example.laive.report.service;

import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.report.ai.ReportAiResult;
import com.example.laive.report.domain.HighlightCandidate;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.repository.SessionReportGenerationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ReportGenerationTransactionService {
    private final SessionReportGenerationRepository repository;
    private final BroadcastOutboxRepository outboxRepository;
    private final OutboxEventFactory eventFactory;
    private final Clock clock;

    public ReportGenerationTransactionService(
            SessionReportGenerationRepository repository,
            BroadcastOutboxRepository outboxRepository,
            OutboxEventFactory eventFactory,
            Clock clock
    ) {
        this.repository = repository;
        this.outboxRepository = outboxRepository;
        this.eventFactory = eventFactory;
        this.clock = clock;
    }

    @Transactional
    public void start(long sessionId, long reportId) {
        List<Long> triggerIds = repository.markTriggersPending(sessionId);
        Instant now = clock.instant();
        if (triggerIds.isEmpty()) {
            if (repository.allTriggersFinished(sessionId)) {
                save(eventFactory.reportFinalizationRequested(sessionId, reportId, now));
            }
            return;
        }
        triggerIds.forEach(triggerId ->
                save(eventFactory.triggerAnalysisRequested(sessionId, reportId, triggerId, now))
        );
    }

    @Transactional
    public void completeTrigger(long sessionId, long reportId, long triggerId, String narrative) {
        repository.completeTrigger(triggerId, narrative, clock.instant());
        scheduleFinalizationWhenReady(sessionId, reportId);
    }

    @Transactional
    public void failTrigger(long sessionId, long reportId, long triggerId, String error) {
        repository.failTrigger(triggerId, sanitize(error), clock.instant());
        scheduleFinalizationWhenReady(sessionId, reportId);
    }

    @Transactional
    public ReportStatus finish(
            long reportId,
            ReportAiResult result,
            List<HighlightCandidate> highlights,
            boolean partial
    ) {
        repository.replaceInsights(reportId, result.insights());
        repository.upsertHighlights(reportId, highlights);
        int updated = partial
                ? repository.partialReport(reportId, result.summary(), clock.instant())
                : repository.completeReport(reportId, result.summary(), clock.instant());
        if (updated != 1) {
            throw new IllegalStateException("PROCESSING 리포트를 완료할 수 없습니다.");
        }
        return partial ? ReportStatus.PARTIAL : ReportStatus.COMPLETED;
    }

    @Transactional
    public void partialWithoutSummary(long reportId) {
        repository.partialReport(reportId, null, clock.instant());
    }

    @Transactional
    public void failReport(long reportId) {
        repository.failReport(reportId);
    }

    private void scheduleFinalizationWhenReady(long sessionId, long reportId) {
        if (repository.allTriggersFinished(sessionId)) {
            save(eventFactory.reportFinalizationRequested(sessionId, reportId, clock.instant()));
        }
    }

    private void save(OutboxEventFactory.PendingOutboxEvent event) {
        outboxRepository.save(
                event.eventId(), event.aggregateId(), event.eventType(),
                event.deduplicationKey(), event.payload(), event.availableAt()
        );
    }

    private String sanitize(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() <= 200 ? error : error.substring(0, 200);
    }
}

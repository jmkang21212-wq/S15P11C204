package com.example.laive.report.service;

import com.example.laive.report.ai.ReportSummaryGenerator;
import com.example.laive.report.ai.ReportSummaryInput;
import com.example.laive.report.config.ReportAiProperties;
import com.example.laive.report.domain.HighlightGenerationContext;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.TriggerAnalysisStatus;
import com.example.laive.report.event.ReportCompletedEvent;
import com.example.laive.report.event.ReportFinalizationRequestedEvent;
import com.example.laive.report.repository.SessionReportGenerationRepository;
import com.example.laive.report.repository.SessionReportQueryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportCompletionService {
    private final SessionReportGenerationRepository generationRepository;
    private final SessionReportQueryRepository queryRepository;
    private final ReportSummaryGenerator summaryGenerator;
    private final HighlightGenerator highlightGenerator;
    private final ReportGenerationTransactionService transactions;
    private final ReportAiProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public ReportCompletionService(
            SessionReportGenerationRepository generationRepository,
            SessionReportQueryRepository queryRepository,
            ReportSummaryGenerator summaryGenerator,
            HighlightGenerator highlightGenerator,
            ReportGenerationTransactionService transactions,
            ReportAiProperties properties,
            ApplicationEventPublisher eventPublisher
    ) {
        this.generationRepository = generationRepository;
        this.queryRepository = queryRepository;
        this.summaryGenerator = summaryGenerator;
        this.highlightGenerator = highlightGenerator;
        this.transactions = transactions;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    public void complete(ReportFinalizationRequestedEvent event) {
        var context = generationRepository.findGenerationContext(event.reportId(), event.sessionId())
                .orElseThrow(() -> new IllegalStateException("리포트 생성 컨텍스트가 없습니다."));
        if (context.report().status() != ReportStatus.PROCESSING) {
            return;
        }
        var triggers = queryRepository.findTriggers(event.sessionId());
        var summary = summaryGenerator.generate(new ReportSummaryInput(
                context,
                triggers,
                generationRepository.findTranscripts(event.sessionId(), 0,
                        context.report().endedAt().toEpochMilli() - context.report().startedAt().toEpochMilli()),
                generationRepository.findRisks(
                        event.sessionId(), context.report().startedAt(), context.report().endedAt()
                ),
                queryRepository.findPlatformMetrics(event.reportId())
        ));
        var highlights = highlightGenerator.generate(new HighlightGenerationContext(
                triggers,
                generationRepository.findRiskTriggerIds(event.sessionId()),
                properties.maxHighlights()
        ));
        boolean failedTrigger = triggers.stream()
                .anyMatch(trigger -> trigger.analysisStatus() == TriggerAnalysisStatus.FAILED);
        double coverage = context.report().totalChat() == 0 ? 1.0
                : (double) context.report().sentimentCounts().analyzedChat()
                / context.report().totalChat();
        boolean partial = failedTrigger || context.hasCollectionGap()
                || coverage < properties.minSentimentCoverage();
        ReportStatus status = transactions.finish(event.reportId(), summary, highlights, partial);
        eventPublisher.publishEvent(new ReportCompletedEvent(
                event.sessionId(), event.reportId(), status, context.report().endedAt()
        ));
    }

    public void partial(ReportFinalizationRequestedEvent event) {
        transactions.partialWithoutSummary(event.reportId());
    }
}

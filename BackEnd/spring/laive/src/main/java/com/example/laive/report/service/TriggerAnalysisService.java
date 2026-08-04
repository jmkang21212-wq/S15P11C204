package com.example.laive.report.service;

import com.example.laive.report.ai.TriggerAnalysisInput;
import com.example.laive.report.ai.TriggerNarrativeGenerator;
import com.example.laive.report.event.TriggerAnalysisRequestedEvent;
import com.example.laive.report.repository.SessionReportGenerationRepository;
import com.example.laive.report.domain.TriggerAnalysisStatus;
import org.springframework.stereotype.Service;

@Service
public class TriggerAnalysisService {
    private final SessionReportGenerationRepository repository;
    private final TriggerNarrativeGenerator generator;
    private final ReportGenerationTransactionService transactions;

    public TriggerAnalysisService(
            SessionReportGenerationRepository repository,
            TriggerNarrativeGenerator generator,
            ReportGenerationTransactionService transactions
    ) {
        this.repository = repository;
        this.generator = generator;
        this.transactions = transactions;
    }

    public void analyze(TriggerAnalysisRequestedEvent event) {
        var context = repository.findGenerationContext(event.reportId(), event.sessionId())
                .orElseThrow(() -> new IllegalStateException("리포트 생성 컨텍스트가 없습니다."));
        var trigger = repository.findTrigger(event.triggerId())
                .orElseThrow(() -> new IllegalStateException("리포트 트리거가 없습니다."));
        if (trigger.analysisStatus() == TriggerAnalysisStatus.COMPLETED) {
            transactions.completeTrigger(
                    event.sessionId(), event.reportId(), event.triggerId(), trigger.narrative()
            );
            return;
        }
        if (trigger.analysisStatus() == TriggerAnalysisStatus.FAILED) {
            transactions.failTrigger(
                    event.sessionId(), event.reportId(), event.triggerId(), "already-failed"
            );
            return;
        }
        var input = new TriggerAnalysisInput(
                context,
                trigger,
                repository.findTranscripts(event.sessionId(), trigger.startMs(), trigger.endMs()),
                repository.findChats(event.sessionId(), context.report().startedAt().plusMillis(trigger.startMs()),
                        context.report().startedAt().plusMillis(trigger.endMs())),
                repository.findRisks(event.sessionId(), context.report().startedAt().plusMillis(trigger.startMs()),
                        context.report().startedAt().plusMillis(trigger.endMs()))
        );
        var narrative = generator.generate(input);
        transactions.completeTrigger(
                event.sessionId(), event.reportId(), event.triggerId(), narrative.narrative()
        );
    }

    public void fail(TriggerAnalysisRequestedEvent event, String error) {
        transactions.failTrigger(
                event.sessionId(), event.reportId(), event.triggerId(), error
        );
    }
}

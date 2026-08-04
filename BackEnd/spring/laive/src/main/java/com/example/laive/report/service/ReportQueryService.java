package com.example.laive.report.service;

import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.SessionHighlight;
import com.example.laive.report.domain.SessionReport;
import com.example.laive.report.domain.SessionReportDetail;
import com.example.laive.report.domain.TriggerAnalysisStatus;
import com.example.laive.report.exception.ReportException;
import com.example.laive.report.repository.SessionReportQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportQueryService {
    private final SessionReportQueryRepository repository;

    public ReportQueryService(SessionReportQueryRepository repository) {
        this.repository = repository;
    }

    public SessionReportDetail get(long streamerId, long sessionId) {
        SessionReport report = requireReport(streamerId, sessionId);
        if (report.status() == ReportStatus.FAILED) {
            throw ReportException.failed();
        }
        var triggers = repository.findTriggers(sessionId);
        double progress = progress(report, triggers);
        if (report.status() == ReportStatus.PROCESSING) {
            return new SessionReportDetail(
                    report, List.of(), triggers, List.of(), List.of(), List.of(), progress);
        }
        return new SessionReportDetail(
                report,
                repository.findViewerTimeline(sessionId),
                triggers,
                repository.findInsights(report.reportId()),
                repository.findHighlights(report.reportId()),
                repository.findPlatformMetrics(report.reportId()),
                progress
        );
    }

    public List<SessionHighlight> getHighlights(long streamerId, long sessionId) {
        SessionReport report = requireReport(streamerId, sessionId);
        if (report.status() == ReportStatus.PROCESSING) {
            throw ReportException.generating();
        }
        if (report.status() == ReportStatus.FAILED) {
            throw ReportException.failed();
        }
        return repository.findHighlights(report.reportId());
    }

    private SessionReport requireReport(long streamerId, long sessionId) {
        if (sessionId <= 0 || !repository.existsOwnedSession(streamerId, sessionId)) {
            throw BroadcastSessionException.notFound();
        }
        return repository.findReport(streamerId, sessionId).orElseThrow(ReportException::notFound);
    }

    private double progress(SessionReport report, List<com.example.laive.report.domain.ReportTrigger> triggers) {
        if (report.status().isTerminal()) {
            return 1.0;
        }
        if (report.avgViewers() == null && report.peakViewers() == null && report.totalChat() == 0) {
            return 0.0;
        }
        long finished = triggers.stream()
                .filter(trigger -> trigger.analysisStatus() == TriggerAnalysisStatus.COMPLETED
                        || trigger.analysisStatus() == TriggerAnalysisStatus.FAILED)
                .count();
        double triggerProgress = triggers.isEmpty() ? 1.0 : (double) finished / triggers.size();
        return Math.min(0.7, 0.2 + triggerProgress * 0.5);
    }
}

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

    /**
     * 열성 시청자 노출 수(FR-F-03). 콘솔의 {@code LiveSnapshotService.TOP_VIEWER_LIMIT}와
     * 같은 값으로 둔다 — 두 화면이 같은 목록 컴포넌트를 쓰는데 개수가 다르면 "방송
     * 중에 보이던 사람이 리포트에서 사라졌다"로 읽힌다.
     */
    private static final int TOP_VIEWER_LIMIT = 5;

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
                    report, List.of(), triggers, List.of(), List.of(), List.of(), List.of(), progress);
        }
        return new SessionReportDetail(
                report,
                repository.findViewerTimeline(sessionId),
                triggers,
                repository.findInsights(report.reportId()),
                repository.findHighlights(report.reportId()),
                repository.findPlatformMetrics(report.reportId()),
                // sessionId 기준이다 — 점수는 reportId가 아니라 세션에 달려 있고,
                // 방송 종료 시점에 이미 확정 저장돼 있어 리포트 생성보다 앞선다.
                repository.findTopViewers(sessionId, TOP_VIEWER_LIMIT),
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

package com.example.laive.report.repository.mybatis;

import com.example.laive.broadcast.fandom.domain.TopViewer;
import com.example.laive.report.domain.ReportInsight;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.SessionHighlight;
import com.example.laive.report.domain.SessionReport;
import com.example.laive.report.domain.ViewerTimelinePoint;
import com.example.laive.report.domain.ReportPlatformMetrics;
import com.example.laive.report.repository.SessionReportQueryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisSessionReportQueryRepository implements SessionReportQueryRepository {
    private final SessionReportMapper mapper;

    public MyBatisSessionReportQueryRepository(SessionReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean existsOwnedSession(long streamerId, long sessionId) {
        return mapper.existsOwnedSession(streamerId, sessionId);
    }

    @Override
    public Optional<SessionReport> findReport(long streamerId, long sessionId) {
        return mapper.findReport(streamerId, sessionId).map(SessionReportRow::toDomain);
    }

    @Override
    public List<ViewerTimelinePoint> findViewerTimeline(long sessionId) {
        return mapper.findViewerTimeline(sessionId);
    }

    @Override
    public List<ReportTrigger> findTriggers(long sessionId) {
        return mapper.findTriggers(sessionId).stream().map(ReportTriggerRow::toDomain).toList();
    }

    @Override
    public List<ReportInsight> findInsights(long reportId) {
        return mapper.findInsights(reportId);
    }

    @Override
    public List<SessionHighlight> findHighlights(long reportId) {
        return mapper.findHighlights(reportId);
    }

    @Override
    public List<ReportPlatformMetrics> findPlatformMetrics(long reportId) {
        return mapper.findPlatformMetrics(reportId);
    }

    @Override
    public List<TopViewer> findTopViewers(long sessionId, int limit) {
        return mapper.findTopViewers(sessionId, limit);
    }
}

package com.example.laive.report.repository;

import com.example.laive.broadcast.fandom.domain.TopViewer;
import com.example.laive.report.domain.ReportInsight;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.SessionHighlight;
import com.example.laive.report.domain.SessionReport;
import com.example.laive.report.domain.ViewerTimelinePoint;
import com.example.laive.report.domain.ReportPlatformMetrics;

import java.util.List;
import java.util.Optional;

public interface SessionReportQueryRepository {
    boolean existsOwnedSession(long streamerId, long sessionId);
    Optional<SessionReport> findReport(long streamerId, long sessionId);
    List<ViewerTimelinePoint> findViewerTimeline(long sessionId);
    List<ReportTrigger> findTriggers(long sessionId);
    List<ReportInsight> findInsights(long reportId);
    List<SessionHighlight> findHighlights(long reportId);
    List<ReportPlatformMetrics> findPlatformMetrics(long reportId);
    List<TopViewer> findTopViewers(long sessionId, int limit);
}

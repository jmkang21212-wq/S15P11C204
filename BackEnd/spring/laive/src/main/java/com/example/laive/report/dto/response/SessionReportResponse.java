package com.example.laive.report.dto.response;

import com.example.laive.media.storage.ClipUrlResolver;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.SessionReportDetail;

import java.time.Instant;
import java.util.List;

public record SessionReportResponse(
        long reportId,
        ReportStatus status,
        ReportSessionResponse session,
        String summary,
        ReportMetricsResponse metrics,
        List<ViewerTimelineResponse> viewerTimeline,
        List<ReportTriggerResponse> triggers,
        List<ReportInsightResponse> insights,
        List<HighlightResponse> highlights,
        List<ReportPlatformMetricsResponse> platformMetrics,
        Instant generatedAt
) {
    public static SessionReportResponse from(SessionReportDetail detail, ClipUrlResolver urlResolver) {
        var report = detail.report();
        return new SessionReportResponse(
                report.reportId(), report.status(), ReportSessionResponse.from(report),
                report.summary(), ReportMetricsResponse.from(report),
                detail.viewerTimeline().stream().map(ViewerTimelineResponse::from).toList(),
                detail.triggers().stream().map(ReportTriggerResponse::from).toList(),
                detail.insights().stream().map(ReportInsightResponse::from).toList(),
                detail.highlights().stream()
                        .map(highlight -> HighlightResponse.from(highlight, urlResolver)).toList(),
                detail.platformMetrics().stream().map(ReportPlatformMetricsResponse::from).toList(),
                report.generatedAt()
        );
    }
}

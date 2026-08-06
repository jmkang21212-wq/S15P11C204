package com.example.laive.report.domain;

import com.example.laive.broadcast.fandom.domain.TopViewer;

import java.util.List;

public record SessionReportDetail(
        SessionReport report,
        List<ViewerTimelinePoint> viewerTimeline,
        List<ReportTrigger> triggers,
        List<ReportInsight> insights,
        List<SessionHighlight> highlights,
        List<ReportPlatformMetrics> platformMetrics,
        List<TopViewer> topViewers,
        double progress
) {
    public SessionReportDetail {
        viewerTimeline = List.copyOf(viewerTimeline);
        triggers = List.copyOf(triggers);
        insights = List.copyOf(insights);
        highlights = List.copyOf(highlights);
        platformMetrics = List.copyOf(platformMetrics);
        topViewers = List.copyOf(topViewers);
    }

    public SessionReportDetail(
            SessionReport report,
            List<ViewerTimelinePoint> viewerTimeline,
            List<ReportTrigger> triggers,
            List<ReportInsight> insights,
            List<SessionHighlight> highlights,
            double progress
    ) {
        this(report, viewerTimeline, triggers, insights, highlights, List.of(), List.of(), progress);
    }
}

package com.example.laive.report.domain;

import java.util.List;

public record SessionReportDetail(
        SessionReport report,
        List<ViewerTimelinePoint> viewerTimeline,
        List<ReportTrigger> triggers,
        List<ReportInsight> insights,
        List<SessionHighlight> highlights,
        List<ReportPlatformMetrics> platformMetrics,
        double progress
) {
    public SessionReportDetail {
        viewerTimeline = List.copyOf(viewerTimeline);
        triggers = List.copyOf(triggers);
        insights = List.copyOf(insights);
        highlights = List.copyOf(highlights);
        platformMetrics = List.copyOf(platformMetrics);
    }

    public SessionReportDetail(
            SessionReport report,
            List<ViewerTimelinePoint> viewerTimeline,
            List<ReportTrigger> triggers,
            List<ReportInsight> insights,
            List<SessionHighlight> highlights,
            double progress
    ) {
        this(report, viewerTimeline, triggers, insights, highlights, List.of(), progress);
    }
}

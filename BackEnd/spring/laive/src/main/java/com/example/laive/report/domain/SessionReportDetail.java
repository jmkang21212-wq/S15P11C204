package com.example.laive.report.domain;

import java.util.List;

public record SessionReportDetail(
        SessionReport report,
        List<ViewerTimelinePoint> viewerTimeline,
        List<ReportTrigger> triggers,
        List<ReportInsight> insights,
        List<SessionHighlight> highlights,
        double progress
) {
    public SessionReportDetail {
        viewerTimeline = List.copyOf(viewerTimeline);
        triggers = List.copyOf(triggers);
        insights = List.copyOf(insights);
        highlights = List.copyOf(highlights);
    }
}

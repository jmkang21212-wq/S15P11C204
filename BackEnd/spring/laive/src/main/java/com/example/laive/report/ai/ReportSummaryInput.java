package com.example.laive.report.ai;

import com.example.laive.report.domain.ReportGenerationContext;
import com.example.laive.report.domain.ReportPlatformMetrics;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.RiskExcerpt;
import com.example.laive.report.domain.TranscriptExcerpt;

import java.util.List;

public record ReportSummaryInput(
        ReportGenerationContext report,
        List<ReportTrigger> triggers,
        List<TranscriptExcerpt> transcripts,
        List<RiskExcerpt> risks,
        List<ReportPlatformMetrics> platformMetrics
) {
    public ReportSummaryInput {
        platformMetrics = platformMetrics == null ? List.of() : List.copyOf(platformMetrics);
    }

    public ReportSummaryInput(
            ReportGenerationContext report,
            List<ReportTrigger> triggers,
            List<TranscriptExcerpt> transcripts,
            List<RiskExcerpt> risks
    ) {
        this(report, triggers, transcripts, risks, List.of());
    }
}

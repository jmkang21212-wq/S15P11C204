package com.example.laive.report.dto.response;

import com.example.laive.report.domain.ReportInsight;

public record ReportInsightResponse(long insightId, String title, String content) {
    public static ReportInsightResponse from(ReportInsight insight) {
        return new ReportInsightResponse(insight.insightId(), insight.title(), insight.content());
    }
}

package com.example.laive.report.ai;

import java.util.List;

public record ReportAiResult(String summary, List<InsightResult> insights) {
    public ReportAiResult {
        if (summary == null || summary.isBlank() || summary.length() > 1000) {
            throw new IllegalArgumentException("리포트 요약은 1~1000자여야 합니다.");
        }
        insights = List.copyOf(insights);
        if (insights.isEmpty() || insights.size() > 3) {
            throw new IllegalArgumentException("인사이트는 1~3개여야 합니다.");
        }
    }
}

package com.example.laive.report.dto.response;

import com.example.laive.broadcast.realtime.domain.SentimentLabel;
import com.example.laive.report.domain.SentimentDistribution;
import com.example.laive.report.domain.SessionReport;

import java.math.BigDecimal;
import java.util.Map;

public record ReportMetricsResponse(
        int avgViewers,
        int peakViewers,
        long totalChat,
        long analyzedChat,
        SentimentLabel dominant,
        Map<SentimentLabel, BigDecimal> distribution
) {
    public static ReportMetricsResponse from(SessionReport report) {
        SentimentDistribution sentiment = SentimentDistribution.from(report.sentimentCounts());
        return new ReportMetricsResponse(
                report.avgViewers() == null ? 0 : report.avgViewers(),
                report.peakViewers() == null ? 0 : report.peakViewers(),
                report.totalChat(),
                report.sentimentCounts().analyzedChat(),
                sentiment.dominant(),
                sentiment.distribution()
        );
    }
}

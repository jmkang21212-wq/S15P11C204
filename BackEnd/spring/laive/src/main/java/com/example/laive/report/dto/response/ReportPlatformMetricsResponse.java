package com.example.laive.report.dto.response;

import com.example.laive.report.domain.ReportPlatformMetrics;

import java.math.BigDecimal;

public record ReportPlatformMetricsResponse(
        long platformSessionId,
        String platformCode,
        String channelName,
        Integer avgViewers,
        Integer peakViewers,
        long totalChat,
        long analyzedChat,
        long broadcastSeconds,
        BigDecimal viewerCoverage,
        String dataQuality
) {
    public static ReportPlatformMetricsResponse from(ReportPlatformMetrics metrics) {
        return new ReportPlatformMetricsResponse(
                metrics.platformSessionId(), metrics.platformCode(), metrics.channelName(),
                metrics.averageViewers(), metrics.peakViewers(), metrics.totalChat(),
                metrics.analyzedChat(), metrics.broadcastSeconds(), metrics.viewerCoverage(),
                metrics.dataQuality()
        );
    }
}

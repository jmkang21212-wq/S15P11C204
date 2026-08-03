package com.example.laive.report.domain;

import java.math.BigDecimal;

/** Finalized metrics for one provider child of a logical broadcast. */
public record ReportPlatformMetrics(
        long platformSessionId,
        String platformCode,
        String channelName,
        Integer averageViewers,
        Integer peakViewers,
        long totalChat,
        long analyzedChat,
        long broadcastSeconds,
        BigDecimal viewerCoverage,
        String dataQuality
) {
}

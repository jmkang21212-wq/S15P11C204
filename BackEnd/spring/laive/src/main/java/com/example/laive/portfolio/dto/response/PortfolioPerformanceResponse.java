package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.PortfolioPerformance;

/**
 * 포트폴리오 응답의 performance 블록이다 (API 12.1).
 */
public record PortfolioPerformanceResponse(
        int avgViewers,
        int peakViewers,
        int broadcastsPerWeek,
        long totalBroadcastCount,
        long totalChat,
        String primaryTimeRange
) {
    public static PortfolioPerformanceResponse from(PortfolioPerformance performance) {
        return new PortfolioPerformanceResponse(
                performance.avgViewers(),
                performance.peakViewers(),
                performance.broadcastsPerWeek(),
                performance.totalBroadcastCount(),
                performance.totalChat(),
                performance.primaryTimeRange());
    }
}

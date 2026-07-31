package com.example.laive.portfolio.domain;

/**
 * portfolios 행의 실적 스냅샷 5컬럼 조회 모델이다.
 */
public record PerformanceSnapshot(
        int avgViewers,
        int peakViewers,
        int broadcastsPerWeek,
        long totalBroadcastCount,
        long totalChat
) {
}

package com.example.laive.portfolio.domain;

/**
 * 방송 실적 블록이다 (API 12.1의 performance).
 *
 * <p>다섯 지표는 portfolios의 스냅샷 컬럼(본인 조회 시 + 일 배치로 갱신)이고,
 * primaryTimeRange만 조회 시 계산하는 필드다(명세가 계산 필드로 못박음).
 */
public record PortfolioPerformance(
        int avgViewers,
        int peakViewers,
        int broadcastsPerWeek,
        long totalBroadcastCount,
        long totalChat,
        String primaryTimeRange
) {
}

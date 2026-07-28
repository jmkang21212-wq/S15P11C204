package com.example.laive.broadcast.session.domain;

import java.math.BigDecimal;

/**
 * 방송 종료 시 PostgreSQL 원천 데이터로 확정한 통계다.
 */
public record SessionStatistics(
        Integer averageViewers,
        Integer peakViewers,
        long totalChat,
        BigDecimal positiveRatio,
        BigDecimal neutralRatio,
        BigDecimal negativeRatio
) {
}


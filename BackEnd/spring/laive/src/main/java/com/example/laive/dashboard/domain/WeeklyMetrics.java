package com.example.laive.dashboard.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyMetrics(
        LocalDate periodStart,
        LocalDate periodEnd,
        int avgViewers,
        BigDecimal avgViewersChangeRate,
        long totalChat,
        BigDecimal totalChatChangeRate
) {
}

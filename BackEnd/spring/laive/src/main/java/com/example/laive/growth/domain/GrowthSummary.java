package com.example.laive.growth.domain;

import java.math.BigDecimal;

public record GrowthSummary(
        Integer averageViewers,
        BigDecimal averageViewersChangeRate,
        long totalChat,
        BigDecimal totalChatChangeRate,
        long broadcastMinutes,
        BigDecimal broadcastMinutesChangeRate
) {
}

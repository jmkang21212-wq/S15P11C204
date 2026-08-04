package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthSummary;

import java.math.BigDecimal;

public record GrowthSummaryResponse(
        Integer avgViewers,
        BigDecimal avgViewersChangeRate,
        long totalChat,
        BigDecimal totalChatChangeRate,
        long broadcastMinutes,
        BigDecimal broadcastMinutesChangeRate
) {
    public static GrowthSummaryResponse from(GrowthSummary summary) {
        return new GrowthSummaryResponse(
                summary.averageViewers(),
                summary.averageViewersChangeRate(),
                summary.totalChat(),
                summary.totalChatChangeRate(),
                summary.broadcastMinutes(),
                summary.broadcastMinutesChangeRate()
        );
    }
}

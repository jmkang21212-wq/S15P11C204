package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthSeriesPoint;

import java.time.LocalDate;

public record GrowthSeriesResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer avgViewers,
        long totalChat,
        long broadcastMinutes
) {
    public static GrowthSeriesResponse from(GrowthSeriesPoint point) {
        return new GrowthSeriesResponse(
                point.periodStart(),
                point.periodEnd(),
                point.averageViewers(),
                point.totalChat(),
                point.broadcastMinutes()
        );
    }
}

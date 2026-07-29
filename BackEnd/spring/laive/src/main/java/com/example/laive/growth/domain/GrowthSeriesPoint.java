package com.example.laive.growth.domain;

import java.time.LocalDate;

public record GrowthSeriesPoint(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer averageViewers,
        long totalChat,
        long broadcastMinutes
) {
}

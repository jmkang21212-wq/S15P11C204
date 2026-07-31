package com.example.laive.growth.roadmap.ai;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RoadmapTrendPoint(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer avgViewers,
        long totalChat,
        long broadcastMinutes,
        BigDecimal broadcastsPerWeek
) {
}

package com.example.laive.growth.domain;

import java.time.LocalDate;
import java.util.List;

public record GrowthReport(
        GrowthPeriodType periodType,
        LocalDate from,
        LocalDate to,
        GrowthSummary summary,
        List<GrowthSeriesPoint> series
) {
}

package com.example.laive.growth.dto.response;

import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthReport;

import java.time.LocalDate;
import java.util.List;

public record GrowthReportResponse(
        GrowthPeriodType periodType,
        LocalDate from,
        LocalDate to,
        String platformCode,
        GrowthSummaryResponse summaryMetrics,
        List<GrowthSeriesResponse> series
) {
    public static GrowthReportResponse from(GrowthReport report) {
        return from(report, "ALL");
    }

    public static GrowthReportResponse from(GrowthReport report, String platformCode) {
        return new GrowthReportResponse(
                report.periodType(),
                report.from(),
                report.to(),
                platformCode,
                GrowthSummaryResponse.from(report.summary()),
                report.series().stream().map(GrowthSeriesResponse::from).toList()
        );
    }
}

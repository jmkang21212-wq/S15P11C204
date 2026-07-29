package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthPeriod;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrowthReportQueryServiceTest {
    @Test
    void fillsMissingBucketsAndCalculatesLastPeriodSummary() {
        GrowthReportQueryRepository repository = mock(GrowthReportQueryRepository.class);
        when(repository.findSeries(
                eq(101L), eq(GrowthPeriodType.WEEKLY), any(GrowthPeriod.class)
        )).thenReturn(List.of(
                new GrowthSeriesPoint(
                        LocalDate.parse("2026-07-06"),
                        LocalDate.parse("2026-07-12"),
                        100, 1000, 100
                ),
                new GrowthSeriesPoint(
                        LocalDate.parse("2026-07-13"),
                        LocalDate.parse("2026-07-19"),
                        120, 1200, 80
                )
        ));
        GrowthReportQueryService service = new GrowthReportQueryService(repository);

        var result = service.getReport(
                101L,
                GrowthPeriodType.WEEKLY,
                LocalDate.parse("2026-06-29"),
                LocalDate.parse("2026-07-19")
        );

        assertThat(result.series()).hasSize(3);
        assertThat(result.series().getFirst().averageViewers()).isNull();
        assertThat(result.summary().averageViewers()).isEqualTo(120);
        assertThat(result.summary().averageViewersChangeRate()).isEqualByComparingTo("0.2000");
        assertThat(result.summary().broadcastMinutesChangeRate()).isEqualByComparingTo("-0.2000");
    }
}

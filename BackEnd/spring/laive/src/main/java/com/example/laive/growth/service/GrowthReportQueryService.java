package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthMath;
import com.example.laive.growth.domain.GrowthPeriod;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthReport;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import com.example.laive.growth.domain.GrowthSummary;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class GrowthReportQueryService {
    private final GrowthReportQueryRepository repository;

    public GrowthReportQueryService(GrowthReportQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public GrowthReport getReport(
            long streamerId,
            GrowthPeriodType periodType,
            LocalDate from,
            LocalDate to
    ) {
        GrowthPeriod period = GrowthPeriod.aligned(periodType, from, to);
        List<GrowthSeriesPoint> series = repository.findSeries(streamerId, periodType, period)
                .stream()
                .map(row -> row.averageViewers() == null
                        ? new GrowthSeriesPoint(
                                row.periodStart(), row.periodEnd(), 0,
                                row.totalChat(), row.broadcastMinutes()
                        )
                        : row)
                .toList();
        List<GrowthSeriesPoint> normalized = normalize(periodType, period, series);
        GrowthSeriesPoint current = normalized.getLast();
        GrowthSeriesPoint previous = normalized.size() > 1
                ? normalized.get(normalized.size() - 2)
                : null;
        GrowthSummary summary = new GrowthSummary(
                current.averageViewers(),
                GrowthMath.changeRate(
                        decimal(current.averageViewers()),
                        previous == null ? null : decimal(previous.averageViewers())
                ),
                current.totalChat(),
                GrowthMath.changeRate(
                        BigDecimal.valueOf(current.totalChat()),
                        previous == null ? null : BigDecimal.valueOf(previous.totalChat())
                ),
                current.broadcastMinutes(),
                GrowthMath.changeRate(
                        BigDecimal.valueOf(current.broadcastMinutes()),
                        previous == null ? null : BigDecimal.valueOf(previous.broadcastMinutes())
                )
        );
        return new GrowthReport(periodType, from, to, summary, normalized);
    }

    private List<GrowthSeriesPoint> normalize(
            GrowthPeriodType type,
            GrowthPeriod period,
            List<GrowthSeriesPoint> rows
    ) {
        var byStart = rows.stream().collect(
                java.util.stream.Collectors.toMap(GrowthSeriesPoint::periodStart, row -> row)
        );
        var result = new java.util.ArrayList<GrowthSeriesPoint>();
        LocalDate cursor = period.start();
        while (!cursor.isAfter(period.end())) {
            LocalDate naturalEnd = type == GrowthPeriodType.WEEKLY
                    ? cursor.plusDays(6)
                    : cursor.plusMonths(1).minusDays(1);
            LocalDate end = naturalEnd.isAfter(period.end()) ? period.end() : naturalEnd;
            result.add(byStart.getOrDefault(
                    cursor,
                    new GrowthSeriesPoint(cursor, end, 0, 0, 0)
            ));
            cursor = end.plusDays(1);
        }
        return List.copyOf(result);
    }

    private BigDecimal decimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}

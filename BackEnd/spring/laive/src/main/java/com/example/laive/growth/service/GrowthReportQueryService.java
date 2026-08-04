package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthMath;
import com.example.laive.growth.domain.GrowthPeriod;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthReport;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import com.example.laive.growth.domain.GrowthSummary;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import com.example.laive.platform.core.PlatformAdapterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class GrowthReportQueryService {
    private final GrowthReportQueryRepository repository;
    private final PlatformAdapterRegistry platformAdapterRegistry;

    public GrowthReportQueryService(GrowthReportQueryRepository repository) {
        this(repository, null);
    }

    @Autowired
    public GrowthReportQueryService(
            GrowthReportQueryRepository repository,
            PlatformAdapterRegistry platformAdapterRegistry
    ) {
        this.repository = repository;
        this.platformAdapterRegistry = platformAdapterRegistry;
    }

    @Transactional(readOnly = true)
    public GrowthReport getReport(
            long streamerId,
            GrowthPeriodType periodType,
            LocalDate from,
            LocalDate to
    ) {
        return getReport(streamerId, periodType, from, to, "ALL");
    }

    @Transactional(readOnly = true)
    public GrowthReport getReport(
            long streamerId,
            GrowthPeriodType periodType,
            LocalDate from,
            LocalDate to,
            String requestedPlatformCode
    ) {
        String platformCode = normalizePlatformCode(requestedPlatformCode);
        GrowthPeriod period = GrowthPeriod.aligned(periodType, from, to);
        List<GrowthSeriesPoint> source = "ALL".equals(platformCode)
                ? repository.findSeries(streamerId, periodType, period)
                : repository.findSeries(streamerId, periodType, period, platformCode);
        List<GrowthSeriesPoint> series = source
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

    private String normalizePlatformCode(String requested) {
        if (requested == null || requested.isBlank() || "ALL".equalsIgnoreCase(requested)) {
            return "ALL";
        }
        if (platformAdapterRegistry == null) {
            return requested.trim().toUpperCase(java.util.Locale.ROOT);
        }
        return platformAdapterRegistry.require(requested).platformCode().value();
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

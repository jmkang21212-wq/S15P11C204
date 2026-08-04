package com.example.laive.dashboard.service;

import com.example.laive.dashboard.domain.Dashboard;
import com.example.laive.dashboard.domain.RecentHighlight;
import com.example.laive.dashboard.domain.WeeklyMetricAggregate;
import com.example.laive.dashboard.domain.WeeklyMetrics;
import com.example.laive.dashboard.repository.DashboardQueryRepository;
import com.example.laive.media.storage.ClipUrlResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

@Service
@Transactional(readOnly = true)
public class DashboardQueryService {
    private static final int PROMISE_LIMIT = 3;
    private static final int HIGHLIGHT_LIMIT = 5;
    private final DashboardQueryRepository repository;
    private final Clock clock;
    private final ClipUrlResolver clipUrlResolver;

    public DashboardQueryService(
            DashboardQueryRepository repository, Clock clock, ClipUrlResolver clipUrlResolver
    ) {
        this.repository = repository;
        this.clock = clock;
        this.clipUrlResolver = clipUrlResolver;
    }

    public Dashboard get(long streamerId) {
        LocalDate currentWeekStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate periodStart = currentWeekStart.minusWeeks(1);
        LocalDate periodEnd = currentWeekStart.minusDays(1);
        LocalDate previousStart = periodStart.minusWeeks(1);

        WeeklyMetricAggregate aggregate = repository.findWeeklyMetrics(
                streamerId,
                previousStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                periodStart.atStartOfDay().toInstant(ZoneOffset.UTC),
                currentWeekStart.atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        return new Dashboard(
                repository.findUpcomingBroadcast(streamerId, clock.instant()).orElse(null),
                repository.findRecentSession(streamerId).orElse(null),
                repository.countPendingPromises(streamerId),
                repository.findPendingPromises(streamerId, PROMISE_LIMIT),
                aggregate.hasCurrentData() ? weekly(periodStart, periodEnd, aggregate) : null,
                repository.findRecentHighlights(streamerId, HIGHLIGHT_LIMIT).stream()
                        .map(this::resolveUrls)
                        .toList()
        );
    }

    /**
     * 매퍼가 DB의 상대 경로(저장 키)를 그대로 돌려주므로(DashboardMapper.xml) 여기서
     * 절대 URL로 조립한다 — SC-07·리포트가 {@code HighlightResponse.from()}에서 하는 것과
     * 같은 단계다. 빈 문자열(클립 생성 전)은 {@code ClipUrlResolver}가 null로 정규화한다.
     */
    private RecentHighlight resolveUrls(RecentHighlight highlight) {
        return new RecentHighlight(
                highlight.highlightId(),
                highlight.durationMs(),
                clipUrlResolver.toUrl(highlight.clipUrl()),
                clipUrlResolver.toUrl(highlight.thumbnailUrl())
        );
    }

    private WeeklyMetrics weekly(
            LocalDate periodStart, LocalDate periodEnd, WeeklyMetricAggregate aggregate
    ) {
        return new WeeklyMetrics(
                periodStart,
                periodEnd,
                aggregate.currentAvgViewers(),
                changeRate(aggregate.currentAvgViewers(), aggregate.previousAvgViewers()),
                aggregate.currentTotalChat(),
                changeRate(aggregate.currentTotalChat(), aggregate.previousTotalChat())
        );
    }

    private BigDecimal changeRate(long current, long previous) {
        if (previous == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP);
    }
}

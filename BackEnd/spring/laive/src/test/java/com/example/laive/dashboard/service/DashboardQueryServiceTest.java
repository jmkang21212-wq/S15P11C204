package com.example.laive.dashboard.service;

import com.example.laive.dashboard.domain.WeeklyMetricAggregate;
import com.example.laive.dashboard.repository.DashboardQueryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private final DashboardQueryRepository repository = mock(DashboardQueryRepository.class);
    private final DashboardQueryService service = new DashboardQueryService(
            repository, Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void usesMostRecentCompletedUtcWeekAndCalculatesChanges() {
        when(repository.findUpcomingBroadcast(eq(101L), any())).thenReturn(java.util.Optional.empty());
        when(repository.findRecentSession(101L)).thenReturn(java.util.Optional.empty());
        when(repository.findPendingPromises(101L, 3)).thenReturn(List.of());
        when(repository.findRecentHighlights(101L, 5)).thenReturn(List.of());
        when(repository.findWeeklyMetrics(eq(101L), any(), any(), any()))
                .thenReturn(new WeeklyMetricAggregate(true, 120, 1200, 100, 1000));

        var result = service.get(101L);

        assertThat(result.weeklyMetrics().periodStart().toString()).isEqualTo("2026-07-20");
        assertThat(result.weeklyMetrics().periodEnd().toString()).isEqualTo("2026-07-26");
        assertThat(result.weeklyMetrics().avgViewersChangeRate())
                .isEqualByComparingTo(new BigDecimal("0.2000"));
        assertThat(result.weeklyMetrics().totalChatChangeRate())
                .isEqualByComparingTo(new BigDecimal("0.2000"));
    }

    @Test
    void returnsNullWeeklyMetricsWhenCompletedWeekHasNoReport() {
        when(repository.findUpcomingBroadcast(eq(101L), any())).thenReturn(java.util.Optional.empty());
        when(repository.findRecentSession(101L)).thenReturn(java.util.Optional.empty());
        when(repository.findPendingPromises(101L, 3)).thenReturn(List.of());
        when(repository.findRecentHighlights(101L, 5)).thenReturn(List.of());
        when(repository.findWeeklyMetrics(eq(101L), any(), any(), any()))
                .thenReturn(new WeeklyMetricAggregate(false, 0, 0, 0, 0));

        assertThat(service.get(101L).weeklyMetrics()).isNull();
    }
}

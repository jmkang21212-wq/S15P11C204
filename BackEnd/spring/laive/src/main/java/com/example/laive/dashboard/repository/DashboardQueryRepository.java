package com.example.laive.dashboard.repository;

import com.example.laive.dashboard.domain.PendingPromise;
import com.example.laive.dashboard.domain.RecentHighlight;
import com.example.laive.dashboard.domain.RecentSession;
import com.example.laive.dashboard.domain.UpcomingBroadcast;
import com.example.laive.dashboard.domain.WeeklyMetricAggregate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DashboardQueryRepository {
    Optional<UpcomingBroadcast> findUpcomingBroadcast(long streamerId, Instant now);
    Optional<RecentSession> findRecentSession(long streamerId);
    long countPendingPromises(long streamerId);
    List<PendingPromise> findPendingPromises(long streamerId, int limit);
    WeeklyMetricAggregate findWeeklyMetrics(
            long streamerId,
            Instant previousStart,
            Instant currentStart,
            Instant currentEnd
    );
    List<RecentHighlight> findRecentHighlights(long streamerId, int limit);
}

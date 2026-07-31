package com.example.laive.dashboard.repository.mybatis;

import com.example.laive.dashboard.domain.PendingPromise;
import com.example.laive.dashboard.domain.RecentHighlight;
import com.example.laive.dashboard.domain.RecentSession;
import com.example.laive.dashboard.domain.UpcomingBroadcast;
import com.example.laive.dashboard.domain.WeeklyMetricAggregate;
import com.example.laive.dashboard.repository.DashboardQueryRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisDashboardQueryRepository implements DashboardQueryRepository {
    private final DashboardMapper mapper;

    public MyBatisDashboardQueryRepository(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<UpcomingBroadcast> findUpcomingBroadcast(long streamerId, Instant now) {
        return mapper.findUpcomingBroadcast(streamerId, now);
    }

    @Override
    public Optional<RecentSession> findRecentSession(long streamerId) {
        return mapper.findRecentSession(streamerId);
    }

    @Override
    public long countPendingPromises(long streamerId) {
        return mapper.countPendingPromises(streamerId);
    }

    @Override
    public List<PendingPromise> findPendingPromises(long streamerId, int limit) {
        return mapper.findPendingPromises(streamerId, limit);
    }

    @Override
    public WeeklyMetricAggregate findWeeklyMetrics(
            long streamerId, Instant previousStart, Instant currentStart, Instant currentEnd
    ) {
        return mapper.findWeeklyMetrics(streamerId, previousStart, currentStart, currentEnd);
    }

    @Override
    public List<RecentHighlight> findRecentHighlights(long streamerId, int limit) {
        return mapper.findRecentHighlights(streamerId, limit);
    }
}

package com.example.laive.dashboard.repository.mybatis;

import com.example.laive.dashboard.domain.PendingPromise;
import com.example.laive.dashboard.domain.RecentHighlight;
import com.example.laive.dashboard.domain.RecentSession;
import com.example.laive.dashboard.domain.UpcomingBroadcast;
import com.example.laive.dashboard.domain.WeeklyMetricAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface DashboardMapper {
    Optional<UpcomingBroadcast> findUpcomingBroadcast(
            @Param("streamerId") long streamerId, @Param("now") Instant now
    );
    Optional<RecentSession> findRecentSession(@Param("streamerId") long streamerId);
    long countPendingPromises(@Param("streamerId") long streamerId);
    List<PendingPromise> findPendingPromises(
            @Param("streamerId") long streamerId, @Param("limit") int limit
    );
    WeeklyMetricAggregate findWeeklyMetrics(
            @Param("streamerId") long streamerId,
            @Param("previousStart") Instant previousStart,
            @Param("currentStart") Instant currentStart,
            @Param("currentEnd") Instant currentEnd
    );
    List<RecentHighlight> findRecentHighlights(
            @Param("streamerId") long streamerId, @Param("limit") int limit
    );
}

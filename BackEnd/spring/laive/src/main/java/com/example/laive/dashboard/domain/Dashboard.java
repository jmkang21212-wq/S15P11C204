package com.example.laive.dashboard.domain;

import java.util.List;

public record Dashboard(
        UpcomingBroadcast upcomingBroadcast,
        RecentSession recentSession,
        long pendingPromiseCount,
        List<PendingPromise> pendingPromises,
        WeeklyMetrics weeklyMetrics,
        List<RecentHighlight> recentHighlights
) {
    public Dashboard {
        pendingPromises = List.copyOf(pendingPromises);
        recentHighlights = List.copyOf(recentHighlights);
    }
}

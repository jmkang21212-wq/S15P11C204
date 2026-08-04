package com.example.laive.dashboard.dto.response;

import com.example.laive.dashboard.domain.Dashboard;
import com.example.laive.dashboard.domain.PendingPromise;
import com.example.laive.dashboard.domain.RecentHighlight;
import com.example.laive.dashboard.domain.RecentSession;
import com.example.laive.dashboard.domain.UpcomingBroadcast;
import com.example.laive.dashboard.domain.WeeklyMetrics;

import java.util.List;

public record DashboardResponse(
        UpcomingBroadcast upcomingBroadcast,
        RecentSession recentSession,
        long pendingPromiseCount,
        List<PendingPromise> pendingPromises,
        WeeklyMetrics weeklyMetrics,
        List<RecentHighlight> recentHighlights
) {
    public static DashboardResponse from(Dashboard dashboard) {
        return new DashboardResponse(
                dashboard.upcomingBroadcast(),
                dashboard.recentSession(),
                dashboard.pendingPromiseCount(),
                dashboard.pendingPromises(),
                dashboard.weeklyMetrics(),
                dashboard.recentHighlights()
        );
    }
}

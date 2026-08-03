package com.example.laive.growth.roadmap.ai;

import java.math.BigDecimal;

public record RecentBroadcastStatistics(
        long broadcastCount,
        BigDecimal broadcastsPerWeek,
        long totalBroadcastMinutes,
        BigDecimal averageBroadcastMinutes,
        BigDecimal averageViewers,
        Integer peakViewers,
        long totalChat,
        BigDecimal chatsPerBroadcast,
        String categoryDistribution
) {
}

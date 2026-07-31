package com.example.laive.growth.ai;

import java.time.Instant;

public record RecentGrowthReport(
        long reportId,
        String title,
        Instant endedAt,
        String status,
        Integer avgViewers,
        Integer peakViewers,
        long totalChat,
        long broadcastMinutes,
        String summary
) {
}

package com.example.laive.growth.ai;

import org.apache.ibatis.annotations.AutomapConstructor;

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
        String summary,
        String platformBreakdown
) {
    @AutomapConstructor
    public RecentGrowthReport {
        platformBreakdown = platformBreakdown == null ? "" : platformBreakdown;
    }

    public RecentGrowthReport(
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
        this(reportId, title, endedAt, status, avgViewers, peakViewers,
                totalChat, broadcastMinutes, summary, "");
    }
}

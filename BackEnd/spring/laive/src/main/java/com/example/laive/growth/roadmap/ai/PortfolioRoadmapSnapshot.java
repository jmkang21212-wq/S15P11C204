package com.example.laive.growth.roadmap.ai;

import java.util.List;

public record PortfolioRoadmapSnapshot(
        String title,
        String introduction,
        List<String> platforms,
        List<String> tags,
        int avgViewers,
        int peakViewers,
        int broadcastsPerWeek,
        long totalBroadcastCount,
        long totalChat,
        List<String> featuredHighlights
) {
    public PortfolioRoadmapSnapshot {
        platforms = List.copyOf(platforms);
        tags = List.copyOf(tags);
        featuredHighlights = List.copyOf(featuredHighlights);
    }
}

package com.example.laive.dashboard.domain;

public record RecentHighlight(
        long highlightId,
        long durationMs,
        String clipUrl,
        String thumbnailUrl
) {
}

package com.example.laive.report.domain;

public record SessionHighlight(
        long highlightId,
        long reportId,
        Long triggerId,
        long startMs,
        long endMs,
        String clipUrl,
        String thumbnailUrl
) {
}

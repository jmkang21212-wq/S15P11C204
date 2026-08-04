package com.example.laive.report.domain;

import com.example.laive.media.domain.ClipStatus;

public record SessionHighlight(
        long highlightId,
        long reportId,
        Long triggerId,
        long startMs,
        long endMs,
        String clipUrl,
        String thumbnailUrl,
        ClipStatus clipStatus
) {
}

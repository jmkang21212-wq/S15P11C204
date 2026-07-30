package com.example.laive.report.dto.response;

import com.example.laive.report.domain.SessionHighlight;

public record HighlightResponse(
        long highlightId,
        Long triggerId,
        long startMs,
        long endMs,
        String clipUrl,
        String thumbnailUrl
) {
    public static HighlightResponse from(SessionHighlight highlight) {
        return new HighlightResponse(
                highlight.highlightId(), highlight.triggerId(), highlight.startMs(),
                highlight.endMs(), highlight.clipUrl(), highlight.thumbnailUrl()
        );
    }
}

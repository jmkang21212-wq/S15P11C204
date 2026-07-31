package com.example.laive.report.dto.response;

import com.example.laive.media.storage.ClipUrlResolver;
import com.example.laive.report.domain.SessionHighlight;

public record HighlightResponse(
        long highlightId,
        Long triggerId,
        long startMs,
        long endMs,
        String clipUrl,
        String thumbnailUrl
) {
    /**
     * DB에는 클립 키만 있으므로 여기서 절대 URL로 조립한다. 클립이 없으면
     * {@code null}이 그대로 나가며, 이는 정상 상태다 — "클립 생성 전·실패 시 NULL"이
     * V1부터의 계약이다(API 1.5).
     */
    public static HighlightResponse from(SessionHighlight highlight, ClipUrlResolver urlResolver) {
        return new HighlightResponse(
                highlight.highlightId(), highlight.triggerId(), highlight.startMs(),
                highlight.endMs(),
                urlResolver.toUrl(highlight.clipUrl()),
                urlResolver.toUrl(highlight.thumbnailUrl())
        );
    }
}

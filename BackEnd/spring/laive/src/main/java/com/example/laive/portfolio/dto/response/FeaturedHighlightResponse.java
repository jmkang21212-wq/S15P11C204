package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.FeaturedHighlightView;

/**
 * 포트폴리오 응답의 대표 하이라이트다 (API 12.1).
 *
 * <p>요청은 highlightId·displayOrder만 보내지만 응답은 clipUrl·thumbnailUrl을
 * 채워 돌려준다 — 프론트 목업이 이 계약을 전제한다. 클립 생성 전이면 null이다.
 */
public record FeaturedHighlightResponse(
        long highlightId,
        int displayOrder,
        String clipUrl,
        String thumbnailUrl
) {
    public static FeaturedHighlightResponse from(FeaturedHighlightView view) {
        return new FeaturedHighlightResponse(
                view.highlightId(), view.displayOrder(), view.clipUrl(), view.thumbnailUrl());
    }
}

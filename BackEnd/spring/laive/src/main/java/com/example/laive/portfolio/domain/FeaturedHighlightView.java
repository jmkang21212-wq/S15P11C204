package com.example.laive.portfolio.domain;

/**
 * 대표 하이라이트 조회 모델이다 (API 12.1의 featuredHighlights 원소).
 *
 * <p>clipUrl·thumbnailUrl은 클립 생성 전이거나 실패하면 null이다 (API 1.5).
 * 요청은 highlightId·displayOrder만 보내지만 응답은 이 둘을 채워 돌려줘야 한다 —
 * 프론트가 그 계약을 전제로 그린다.
 */
public record FeaturedHighlightView(
        long highlightId,
        int displayOrder,
        String clipUrl,
        String thumbnailUrl
) {
}

package com.example.laive.portfolio.dto.request;

import com.example.laive.portfolio.domain.FeaturedHighlightSelection;
import com.example.laive.portfolio.exception.PortfolioException;

/**
 * 대표 하이라이트 지정 입력 한 건이다 (API 12.2).
 *
 * <p>요청은 이 두 필드만 보낸다. clipUrl·thumbnailUrl은 서버가 응답에서 채운다.
 */
public record FeaturedHighlightRequest(
        Long highlightId,
        Integer displayOrder
) {
    public FeaturedHighlightSelection toSelection() {
        if (highlightId == null || displayOrder == null) {
            throw PortfolioException.highlightInvalid();
        }
        return new FeaturedHighlightSelection(highlightId, displayOrder);
    }
}

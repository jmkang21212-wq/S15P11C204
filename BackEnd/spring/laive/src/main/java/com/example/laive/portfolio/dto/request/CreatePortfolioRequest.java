package com.example.laive.portfolio.dto.request;

import com.example.laive.portfolio.domain.CreatePortfolioCommand;
import com.example.laive.portfolio.domain.FeaturedHighlightSelection;

import java.util.List;

/**
 * 포트폴리오 생성 API 입력이다 (API 12 신설 — POST /portfolio/me).
 *
 * <p>자동 구성이 없으므로 title·tagIds·featuredHighlights·isPublic은 필수다.
 * 필수 검증은 Command 생성자에서 하며, 누락 시 PORTFOLIO_INVALID_FIELD 400이 된다.
 */
public record CreatePortfolioRequest(
        String title,
        String introduction,
        String contactEmail,
        String contactUrl,
        Boolean isPublic,
        List<Long> tagIds,
        List<FeaturedHighlightRequest> featuredHighlights
) {
    public CreatePortfolioCommand toCommand() {
        List<FeaturedHighlightSelection> selections = featuredHighlights == null
                ? null
                : featuredHighlights.stream().map(FeaturedHighlightRequest::toSelection).toList();
        return new CreatePortfolioCommand(
                title, introduction, contactEmail, contactUrl, isPublic, tagIds, selections);
    }
}

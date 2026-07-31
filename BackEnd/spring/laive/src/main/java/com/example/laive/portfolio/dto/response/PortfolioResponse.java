package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.PortfolioDetail;
import com.example.laive.streamer.dto.response.TagResponse;

import java.time.Instant;
import java.util.List;

/**
 * 내 포트폴리오 응답이다 (API 12.1).
 *
 * <p>필드 구성은 프론트 타입(FrontEnd types/portfolio.ts Portfolio)과 맞춘 것이므로
 * 이름을 바꾸지 말 것. publishedAt은 아직 공개한 적 없으면 null이다 (API 1.5).
 */
public record PortfolioResponse(
        long portfolioId,
        String publicSlug,
        String title,
        String introduction,
        String contactEmail,
        String contactUrl,
        boolean isPublic,
        Instant publishedAt,
        PortfolioProfileResponse profile,
        List<TagResponse> tags,
        PortfolioPerformanceResponse performance,
        List<FeaturedHighlightResponse> featuredHighlights
) {
    public static PortfolioResponse from(PortfolioDetail detail) {
        return new PortfolioResponse(
                detail.portfolio().portfolioId(),
                detail.portfolio().publicSlug(),
                detail.portfolio().title(),
                detail.portfolio().introduction(),
                detail.portfolio().contactEmail(),
                detail.portfolio().contactUrl(),
                detail.portfolio().isPublic(),
                detail.portfolio().publishedAt(),
                PortfolioProfileResponse.from(detail.profile()),
                detail.tags().stream().map(TagResponse::from).toList(),
                PortfolioPerformanceResponse.from(detail.performance()),
                detail.featuredHighlights().stream().map(FeaturedHighlightResponse::from).toList()
        );
    }
}

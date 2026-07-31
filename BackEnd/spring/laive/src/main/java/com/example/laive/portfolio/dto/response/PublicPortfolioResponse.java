package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.PortfolioDetail;
import com.example.laive.streamer.dto.response.TagResponse;

import java.util.List;

/**
 * 공개 포트폴리오 응답이다 (API 12.4).
 *
 * <p>내 포트폴리오 응답에서 isPublic·publishedAt 같은 편집용 상태를 뺀 형태다
 * (프론트 타입 {@code PublicPortfolio = Omit<Portfolio, 'isPublic' | 'publishedAt'>}).
 * 별도 record로 두는 이유: 필드 자체가 없어야 직렬화 실수로도 새지 않는다.
 */
public record PublicPortfolioResponse(
        long portfolioId,
        String publicSlug,
        String title,
        String introduction,
        String contactEmail,
        String contactUrl,
        PortfolioProfileResponse profile,
        List<TagResponse> tags,
        PortfolioPerformanceResponse performance,
        List<FeaturedHighlightResponse> featuredHighlights
) {
    public static PublicPortfolioResponse from(PortfolioDetail detail) {
        return new PublicPortfolioResponse(
                detail.portfolio().portfolioId(),
                detail.portfolio().publicSlug(),
                detail.portfolio().title(),
                detail.portfolio().introduction(),
                detail.portfolio().contactEmail(),
                detail.portfolio().contactUrl(),
                PortfolioProfileResponse.from(detail.profile()),
                detail.tags().stream().map(TagResponse::from).toList(),
                PortfolioPerformanceResponse.from(detail.performance()),
                detail.featuredHighlights().stream().map(FeaturedHighlightResponse::from).toList()
        );
    }
}

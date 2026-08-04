package com.example.laive.portfolio.domain;

import com.example.laive.streamer.domain.StreamerTag;

import java.util.List;

/**
 * 포트폴리오 한 화면 분량의 조립 결과다 (API 12.1 응답의 도메인 형태).
 *
 * <p>본체(portfolios) + 프로필 조인 + 태그 조인 + 실적 + 대표 하이라이트를 묶는다.
 * 내 조회·공개 조회가 같은 조립을 쓰고, 응답 DTO에서 노출 필드만 갈린다.
 */
public record PortfolioDetail(
        Portfolio portfolio,
        PortfolioProfileView profile,
        List<StreamerTag> tags,
        PortfolioPerformance performance,
        List<FeaturedHighlightView> featuredHighlights
) {
    public PortfolioDetail {
        tags = List.copyOf(tags);
        featuredHighlights = List.copyOf(featuredHighlights);
    }
}

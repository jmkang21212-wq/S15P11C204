package com.example.laive.portfolio.domain;

/**
 * 검색 페이지 쿼리 한 행이다 (API 12.3 — 1단계 조회).
 *
 * <p>목록 안의 배열(platformAccounts·categoryTags)은 조인하면 페이징이 꼬이므로
 * 여기서는 스칼라만 뽑고, 배열은 페이지에 든 streamerId들로 2단계 일괄 조회한다.
 */
public record PortfolioSearchRow(
        long portfolioId,
        String publicSlug,
        long streamerId,
        String nickname,
        String profileImageUrl,
        int avgViewers,
        int broadcastsPerWeek
) {
}

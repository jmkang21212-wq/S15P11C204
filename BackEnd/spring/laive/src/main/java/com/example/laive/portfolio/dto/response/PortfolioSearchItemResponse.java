package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.PortfolioSearchItem;

import java.util.List;

/**
 * 공개 포트폴리오 검색 결과 한 건이다 (API 12.3 items 원소).
 *
 * <p>categoryTags는 12.1의 tags(객체 배열)와 달리 문자열 배열이다 — 명세와 프론트
 * 타입(types/portfolioSearch.ts)이 그렇게 못박았다.
 */
public record PortfolioSearchItemResponse(
        long portfolioId,
        String publicSlug,
        String nickname,
        String profileImageUrl,
        List<PlatformAccountResponse> platformAccounts,
        List<String> categoryTags,
        int avgViewers,
        int broadcastsPerWeek
) {
    public static PortfolioSearchItemResponse from(PortfolioSearchItem item) {
        return new PortfolioSearchItemResponse(
                item.portfolioId(),
                item.publicSlug(),
                item.nickname(),
                item.profileImageUrl(),
                item.platformAccounts().stream().map(PlatformAccountResponse::from).toList(),
                item.categoryTags(),
                item.avgViewers(),
                item.broadcastsPerWeek()
        );
    }
}

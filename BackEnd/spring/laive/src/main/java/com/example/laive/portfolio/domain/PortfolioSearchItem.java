package com.example.laive.portfolio.domain;

import java.util.List;

/**
 * 검색 결과 한 건의 조립 결과다 (API 12.3 items 원소).
 *
 * <p>categoryTags는 12.1의 tags(객체 배열)와 달리 문자열 배열이다 — 명세·프론트
 * 타입(types/portfolioSearch.ts)이 그렇게 못박았다.
 */
public record PortfolioSearchItem(
        long portfolioId,
        String publicSlug,
        String nickname,
        String profileImageUrl,
        List<PlatformAccountView> platformAccounts,
        List<String> categoryTags,
        int avgViewers,
        int broadcastsPerWeek
) {
    public PortfolioSearchItem {
        platformAccounts = List.copyOf(platformAccounts);
        categoryTags = List.copyOf(categoryTags);
    }
}

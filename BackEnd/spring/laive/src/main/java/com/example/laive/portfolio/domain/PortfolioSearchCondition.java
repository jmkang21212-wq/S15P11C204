package com.example.laive.portfolio.domain;

/**
 * 공개 포트폴리오 검색 조건이다 (API 12.3).
 *
 * <p>keyword는 채널명 또는 태그명 부분 일치, tag는 태그명 정확 일치다.
 * 빈 문자열은 조건 없음(null)으로 눕혀 SQL 분기를 단순하게 유지한다.
 */
public record PortfolioSearchCondition(
        String keyword,
        String platform,
        String tag,
        Integer minAvgViewers,
        Integer maxAvgViewers,
        PortfolioSort sort
) {
    public PortfolioSearchCondition {
        keyword = blankToNull(keyword);
        platform = blankToNull(platform);
        tag = blankToNull(tag);
        sort = sort == null ? PortfolioSort.RECENT : sort;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

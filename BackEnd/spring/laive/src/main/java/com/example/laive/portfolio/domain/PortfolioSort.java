package com.example.laive.portfolio.domain;

/**
 * 공개 포트폴리오 검색 정렬이다 (API 12.3).
 */
public enum PortfolioSort {
    /** 최근 공개 순 (published_at DESC). 공개 행은 published_at이 항상 있다. */
    RECENT,
    AVG_VIEWERS_DESC,
    BROADCASTS_DESC
}

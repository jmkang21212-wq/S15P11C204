package com.example.laive.portfolio.domain;

/**
 * 검색 2단계 일괄 조회용 — streamerId가 붙은 태그명 행이다 (categoryTags 재료).
 */
public record TagNameRow(
        long streamerId,
        String name
) {
}

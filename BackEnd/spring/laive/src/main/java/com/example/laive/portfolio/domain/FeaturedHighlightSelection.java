package com.example.laive.portfolio.domain;

import com.example.laive.portfolio.exception.PortfolioException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 대표 하이라이트 지정 한 건이다 (API 12.2 featuredHighlights 원소).
 *
 * <p>개수 상한은 없다 — 방송이 쌓일수록 하이라이트도 늘어나는 자원이라
 * "노출할 것을 고른다"만 강제한다.
 */
public record FeaturedHighlightSelection(
        long highlightId,
        int displayOrder
) {
    public FeaturedHighlightSelection {
        if (highlightId <= 0 || displayOrder <= 0) {
            throw PortfolioException.highlightInvalid();
        }
    }

    /**
     * 같은 하이라이트를 두 번 고정하거나 노출 순서가 겹치면 거절한다.
     * DB의 PK·UNIQUE 제약이 잡기 전에 의미 있는 400으로 돌려주기 위함이다.
     */
    public static void requireNoDuplicates(List<FeaturedHighlightSelection> selections) {
        Set<Long> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (FeaturedHighlightSelection selection : selections) {
            if (!ids.add(selection.highlightId()) || !orders.add(selection.displayOrder())) {
                throw PortfolioException.highlightInvalid();
            }
        }
    }
}

package com.example.laive.portfolio.dto.request;

import com.example.laive.common.api.PatchValue;
import com.example.laive.portfolio.domain.FeaturedHighlightSelection;
import com.example.laive.portfolio.domain.UpdatePortfolioCommand;

import java.util.List;

/**
 * 포트폴리오 부분 수정 API 입력이다 (API 12.2).
 *
 * <p>publicSlug·tags·실적은 수정 대상이 아니다 — 태그는 프로필(6.6),
 * 실적은 백엔드 집계의 몫이다. 값 범위 검증은 도메인에 맡긴다. PatchValue로
 * 감싸면 jakarta 애노테이션이 안쪽 값에 닿지 않기 때문이다.
 */
public record UpdatePortfolioRequest(
        PatchValue<String> title,
        PatchValue<String> introduction,
        PatchValue<String> contactEmail,
        PatchValue<String> contactUrl,
        PatchValue<Boolean> isPublic,
        PatchValue<List<FeaturedHighlightRequest>> featuredHighlights
) {
    public UpdatePortfolioCommand toCommand() {
        return new UpdatePortfolioCommand(
                title, introduction, contactEmail, contactUrl, isPublic, toSelections());
    }

    private PatchValue<List<FeaturedHighlightSelection>> toSelections() {
        if (featuredHighlights == null) {
            return null;
        }
        List<FeaturedHighlightRequest> value = featuredHighlights.value();
        return PatchValue.of(value == null
                ? null
                : value.stream().map(FeaturedHighlightRequest::toSelection).toList());
    }
}

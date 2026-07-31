package com.example.laive.portfolio.domain;

import com.example.laive.common.api.PatchValue;

import java.util.List;

/**
 * 포트폴리오 부분 수정 입력이다 (API 12.2).
 *
 * <p>featuredHighlights는 전달되면 전체 치환이다. 빈 배열·명시적 null 모두
 * "전부 해제"를 뜻한다. publicSlug·tags·실적은 이 API의 수정 대상이 아니다 —
 * 태그는 프로필(6.6), 실적은 백엔드 집계의 몫이다.
 */
public record UpdatePortfolioCommand(
        PatchValue<String> title,
        PatchValue<String> introduction,
        PatchValue<String> contactEmail,
        PatchValue<String> contactUrl,
        PatchValue<Boolean> isPublic,
        PatchValue<List<FeaturedHighlightSelection>> featuredHighlights
) {
    /** 아무 필드도 보내지 않은 요청인지 판단한다. 그런 PATCH는 400으로 거절한다. */
    public boolean isEmpty() {
        return title == null
                && introduction == null
                && contactEmail == null
                && contactUrl == null
                && isPublic == null
                && featuredHighlights == null;
    }
}

package com.example.laive.portfolio.domain;

import com.example.laive.portfolio.exception.PortfolioException;

import java.util.List;

/**
 * 포트폴리오 생성 입력이다 (API 12 신설 — POST /portfolio/me).
 *
 * <p>자동 구성을 폐기했으므로 생성 = 사용자가 설정을 마치는 행위다. title·tagIds·
 * featuredHighlights·isPublic은 필수이고, 백엔드가 대신 채워주는 기본값은 없다.
 * featuredHighlights는 배열 자체가 필수지만 0개는 허용한다 — 하이라이트가 안 뽑힌
 * 방송도 있어 1개 이상을 강제하면 생성 자체가 막히는 스트리머가 생긴다.
 *
 * <p>필드 존재 여부는 여기서, 값 범위는 {@link Portfolio} 생성자에서 검증한다.
 */
public record CreatePortfolioCommand(
        String title,
        String introduction,
        String contactEmail,
        String contactUrl,
        Boolean isPublic,
        List<Long> tagIds,
        List<FeaturedHighlightSelection> featuredHighlights
) {
    public CreatePortfolioCommand {
        if (title == null || title.isBlank()) {
            throw PortfolioException.invalidField("title은 필수입니다.");
        }
        if (isPublic == null) {
            throw PortfolioException.invalidField("isPublic는 필수입니다. 공개 여부를 정해야 생성됩니다.");
        }
        if (tagIds == null || tagIds.isEmpty()) {
            throw PortfolioException.invalidField("tagIds는 1개 이상 필수입니다.");
        }
        if (featuredHighlights == null) {
            throw PortfolioException.invalidField(
                    "featuredHighlights 배열은 필수입니다. 고정할 것이 없으면 빈 배열을 보내세요.");
        }
        tagIds = List.copyOf(tagIds);
        featuredHighlights = List.copyOf(featuredHighlights);
    }
}

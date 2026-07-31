package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.PortfolioProfileView;

import java.util.List;

/**
 * 포트폴리오 응답의 profile 블록이다 (API 12.1).
 */
public record PortfolioProfileResponse(
        String nickname,
        String profileImageUrl,
        List<PlatformAccountResponse> platformAccounts
) {
    public static PortfolioProfileResponse from(PortfolioProfileView view) {
        return new PortfolioProfileResponse(
                view.nickname(),
                view.profileImageUrl(),
                view.platformAccounts().stream().map(PlatformAccountResponse::from).toList());
    }
}

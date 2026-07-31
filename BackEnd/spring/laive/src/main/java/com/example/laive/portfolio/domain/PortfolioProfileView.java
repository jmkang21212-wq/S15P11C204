package com.example.laive.portfolio.domain;

import java.util.List;

/**
 * 포트폴리오에 붙는 프로필 블록이다 (API 12.1의 profile).
 *
 * <p>포트폴리오가 소유한 데이터가 아니라 STREAMERS·PLATFORM_ACCOUNTS를 조회 시
 * 조인한 결과다. 편집은 각각 프로필(6.6)·플랫폼 연동(SC-01b)에서 한다.
 */
public record PortfolioProfileView(
        String nickname,
        String profileImageUrl,
        List<PlatformAccountView> platformAccounts
) {
    public PortfolioProfileView {
        platformAccounts = List.copyOf(platformAccounts);
    }
}

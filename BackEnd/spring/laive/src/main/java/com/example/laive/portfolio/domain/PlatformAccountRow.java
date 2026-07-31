package com.example.laive.portfolio.domain;

/**
 * 검색 2단계 일괄 조회용 — streamerId가 붙은 플랫폼 계정 행이다.
 */
public record PlatformAccountRow(
        long streamerId,
        long platformAccountId,
        String platform,
        String channelId,
        String channelName
) {
    public PlatformAccountView toView() {
        return new PlatformAccountView(platformAccountId, platform, channelId, channelName);
    }
}

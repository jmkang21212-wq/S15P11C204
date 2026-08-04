package com.example.laive.portfolio.domain;

/**
 * 포트폴리오 응답에 싣는 플랫폼 계정의 공개 안전 필드 4개다 (API 1.6, 12.1).
 *
 * <p>connectionStatus·connectedAt·토큰은 의도적으로 없다 — 공개 포트폴리오는
 * 익명이 보는 응답이라 계정 상태를 실으면 안 된다. 필드 자체를 만들지 않아
 * 실수로도 새지 않게 한다.
 */
public record PlatformAccountView(
        long platformAccountId,
        String platform,
        String channelId,
        String channelName
) {
}

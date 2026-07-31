package com.example.laive.portfolio.dto.response;

import com.example.laive.portfolio.domain.PlatformAccountView;

/**
 * 포트폴리오 응답의 플랫폼 계정이다 (API 1.6 — 항상 배열로 반환).
 *
 * <p>플랫폼 연동 화면(SC-01b)의 6필드 응답과 달리 공개 안전한 4필드만이다.
 * connectionStatus·connectedAt을 여기 추가하지 말 것 — 익명이 보는 응답이다.
 */
public record PlatformAccountResponse(
        long platformAccountId,
        String platform,
        String channelId,
        String channelName
) {
    public static PlatformAccountResponse from(PlatformAccountView view) {
        return new PlatformAccountResponse(
                view.platformAccountId(), view.platform(), view.channelId(), view.channelName());
    }
}

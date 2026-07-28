package com.example.laive.broadcast.session.repository;

/**
 * 방송 시작 검증에 필요한 플랫폼 계정 최소 정보다.
 */
public record PlatformAccountReference(
        long platformAccountId,
        long streamerId,
        String platform,
        String connectionStatus
) {
}


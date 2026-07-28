package com.example.laive.platform.domain;

import java.time.Instant;

/**
 * 연동 생성·재연동 시 저장할 값들을 모은 쓰기 모델이다.
 * accessToken, refreshToken은 이미 암호화된 값이어야 한다.
 */
public record PlatformAccountUpsert(
        long streamerId,
        String platform,
        String channelId,
        String channelName,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String scope
) {
}

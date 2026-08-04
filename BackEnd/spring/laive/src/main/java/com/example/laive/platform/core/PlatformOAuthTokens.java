package com.example.laive.platform.core;

import java.time.Instant;

/** OAuth 공급자마다 다른 응답을 코어가 저장할 수 있는 공통 토큰 모델로 정규화한다. */
public record PlatformOAuthTokens(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String scope
) {
}

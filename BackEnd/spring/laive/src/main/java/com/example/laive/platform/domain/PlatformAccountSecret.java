package com.example.laive.platform.domain;

import java.time.Instant;

/**
 * 토큰 갱신과 연동 해제에만 쓰는 내부 모델이다.
 * accessToken, refreshToken은 암호화된 상태의 값이며 API 응답에 그대로 노출해서는 안 된다.
 *
 * MyBatis가 생성자 파라미터 순서로 매핑하므로 SELECT 컬럼 순서와 이 순서를 함께 유지해야 한다.
 */
public record PlatformAccountSecret(
        long platformAccountId,
        long streamerId,
        String platform,
        String channelId,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String connectionStatus
) {
    public boolean isReauthRequired() {
        return "REAUTH_REQUIRED".equals(connectionStatus);
    }
}

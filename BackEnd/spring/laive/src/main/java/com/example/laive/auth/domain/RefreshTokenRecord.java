package com.example.laive.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Refresh Token 유효성 검사와 회전에 필요한 DB 행 모델이다.
 */
public record RefreshTokenRecord(
        long id,
        long streamerId,
        Instant expiresAt,
        Instant revokedAt
) {
    /**
     * 만료 전이면서, 폐기되지 않았거나 폐기 후 유예 기간 안인 토큰인지 확인한다.
     * 유예 기간은 여러 창이 같은 쿠키로 거의 동시에 회전을 요청할 때
     * 뒤늦게 도착한 요청이 로그아웃되지 않도록 하기 위한 것이다.
     */
    public boolean isUsableAt(Instant now, Duration rotationGrace) {
        if (!expiresAt.isAfter(now)) {
            return false;
        }
        return revokedAt == null || revokedAt.plus(rotationGrace).isAfter(now);
    }
}

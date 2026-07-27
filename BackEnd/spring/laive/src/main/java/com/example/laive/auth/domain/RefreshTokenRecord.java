package com.example.laive.auth.domain;

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
    /** 폐기되지 않았고 현재 시각보다 만료 시각이 뒤인 토큰인지 확인한다. */
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}

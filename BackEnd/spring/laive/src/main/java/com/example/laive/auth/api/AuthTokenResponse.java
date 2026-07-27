package com.example.laive.auth.api;

import com.example.laive.auth.domain.Streamer;

/**
 * Refresh 성공 시 반환하는 Access Token과 로그인 스트리머 요약 정보다.
 */
public record AuthTokenResponse(
        String accessToken,
        long expiresIn,
        Streamer streamer
) {
}

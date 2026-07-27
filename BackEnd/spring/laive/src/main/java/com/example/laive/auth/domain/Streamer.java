package com.example.laive.auth.domain;

/**
 * 인증 응답과 소셜 계정 조회에 사용하는 스트리머 요약 모델이다.
 */
public record Streamer(
        long streamerId,
        String email,
        String nickname,
        String profileImageUrl
) {
}

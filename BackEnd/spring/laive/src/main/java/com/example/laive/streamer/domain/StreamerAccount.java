package com.example.laive.streamer.domain;

/**
 * streamers 테이블 한 행의 프로필 관점 조회 모델이다.
 *
 * <p>password_hash 원문은 절대 실어 나르지 않는다 — 존재 여부(hasPassword)만 SQL에서
 * boolean으로 바꿔 가져온다 (API 6.6의 hasPassword 필드).
 */
public record StreamerAccount(
        long streamerId,
        String email,
        boolean hasPassword,
        String nickname,
        String profileImageUrl,
        String bio
) {
}

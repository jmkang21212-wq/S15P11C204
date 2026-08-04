package com.example.laive.portfolio.domain;

/**
 * 포트폴리오 profile 블록에 필요한 streamers 행의 최소 조회 모델이다.
 */
public record StreamerBasics(
        String nickname,
        String profileImageUrl
) {
}

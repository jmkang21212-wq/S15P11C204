package com.example.laive.streamer.domain;

import java.util.List;

/**
 * GET /streamers/me 한 화면 분량의 조립 결과다 (API 6.6).
 *
 * <p>loginMethods는 저장된 컬럼이 아니라 계정 상태에서 계산한다 —
 * password_hash가 있으면 EMAIL, social_accounts의 provider가 그 뒤를 잇는다.
 */
public record StreamerProfile(
        long streamerId,
        String email,
        boolean hasPassword,
        List<String> loginMethods,
        String nickname,
        String profileImageUrl,
        String bio,
        List<StreamerTag> tags
) {
    public StreamerProfile {
        loginMethods = List.copyOf(loginMethods);
        tags = List.copyOf(tags);
    }
}

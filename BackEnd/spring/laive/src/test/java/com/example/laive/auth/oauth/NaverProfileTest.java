package com.example.laive.auth.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NaverProfileTest {

    @Test
    void normalizesEmailAndUsesNaverNickname() {
        NaverProfile profile = new NaverProfile(
                "provider-id",
                "  USER@Example.COM ",
                " 스트리머 ",
                "실명",
                " https://example.com/profile.jpg "
        );

        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.resolvedNickname()).isEqualTo("스트리머");
        assertThat(profile.profileImage()).isEqualTo("https://example.com/profile.jpg");
    }

    @Test
    void fallsBackToNameWhenNicknameWasNotProvided() {
        NaverProfile profile = new NaverProfile("provider-id", null, null, "네이버 사용자", null);

        assertThat(profile.resolvedNickname()).isEqualTo("네이버 사용자");
    }

    @Test
    void fallsBackToProviderIdWithoutOptionalProfileFields() {
        NaverProfile profile = new NaverProfile("1234567890abcdef", null, null, null, null);

        assertThat(profile.resolvedNickname()).isEqualTo("naver_90abcdef");
    }
}

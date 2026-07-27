package com.example.laive.auth.oauth;

import java.util.Locale;

/**
 * 네이버 사용자 정보 응답 중 서비스 회원 생성에 필요한 필드만 표현한다.
 */
public record NaverProfile(
        String id,
        String email,
        String nickname,
        String name,
        String profileImage
) {
    public NaverProfile {
        // 선택 제공 필드의 공백 값을 null로 통일하고 이메일 비교 형식을 소문자로 맞춘다.
        id = trimToNull(id);
        email = normalizeEmail(email);
        nickname = trimToNull(nickname);
        name = trimToNull(name);
        profileImage = trimToNull(profileImage);
    }

    /**
     * 닉네임 미제공 시 이름, provider ID 순서로 안전한 기본 닉네임을 만든다.
     */
    public String resolvedNickname() {
        String candidate = nickname != null ? nickname : name;
        if (candidate == null) {
            String suffix = id.length() <= 8 ? id : id.substring(id.length() - 8);
            candidate = "naver_" + suffix;
        }
        return candidate.length() <= 50 ? candidate : candidate.substring(0, 50);
    }

    private static String normalizeEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

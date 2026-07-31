package com.example.laive.streamer.dto.response;

import com.example.laive.streamer.domain.StreamerProfile;

import java.util.List;

/**
 * 내 프로필 응답이다 (API 6.6).
 */
public record StreamerProfileResponse(
        long streamerId,
        String email,
        boolean hasPassword,
        List<String> loginMethods,
        String nickname,
        String profileImageUrl,
        String bio,
        List<TagResponse> tags
) {
    public static StreamerProfileResponse from(StreamerProfile profile) {
        return new StreamerProfileResponse(
                profile.streamerId(),
                profile.email(),
                profile.hasPassword(),
                profile.loginMethods(),
                profile.nickname(),
                profile.profileImageUrl(),
                profile.bio(),
                profile.tags().stream().map(TagResponse::from).toList()
        );
    }
}

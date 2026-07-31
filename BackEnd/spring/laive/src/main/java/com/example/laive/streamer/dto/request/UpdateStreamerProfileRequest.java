package com.example.laive.streamer.dto.request;

import com.example.laive.common.api.PatchValue;
import com.example.laive.streamer.domain.UpdateStreamerProfileCommand;

import java.util.List;

/**
 * 프로필 부분 수정 API 입력이다 (API 6.6).
 *
 * <p>수정 가능 필드는 명세대로 nickname·profileImageUrl·bio·tagIds 넷이다.
 * 값 범위 검증은 서비스에 맡긴다 — PatchValue로 감싸면 jakarta 애노테이션이
 * 안쪽 값에 닿지 않기 때문이다.
 */
public record UpdateStreamerProfileRequest(
        PatchValue<String> nickname,
        PatchValue<String> profileImageUrl,
        PatchValue<String> bio,
        PatchValue<List<Long>> tagIds
) {
    public UpdateStreamerProfileCommand toCommand() {
        return new UpdateStreamerProfileCommand(nickname, profileImageUrl, bio, tagIds);
    }
}

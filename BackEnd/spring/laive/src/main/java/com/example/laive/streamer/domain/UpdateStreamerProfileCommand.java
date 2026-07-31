package com.example.laive.streamer.domain;

import com.example.laive.common.api.PatchValue;

import java.util.List;

/**
 * 프로필 부분 수정 입력이다 (API 6.6).
 *
 * <p>PatchValue로 "필드 미전송"과 "명시적 null"을 구분한다. bio·profileImageUrl은
 * null로 비울 수 있는 필드라 이 구분이 없으면 되돌릴 방법이 없다.
 * tagIds에 명시적 null 또는 빈 배열을 보내면 태그를 전부 비운다.
 */
public record UpdateStreamerProfileCommand(
        PatchValue<String> nickname,
        PatchValue<String> profileImageUrl,
        PatchValue<String> bio,
        PatchValue<List<Long>> tagIds
) {
    /** 아무 필드도 보내지 않은 요청인지 판단한다. 그런 PATCH는 400으로 거절한다. */
    public boolean isEmpty() {
        return nickname == null
                && profileImageUrl == null
                && bio == null
                && tagIds == null;
    }
}

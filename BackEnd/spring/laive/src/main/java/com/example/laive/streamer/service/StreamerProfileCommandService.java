package com.example.laive.streamer.service;

import com.example.laive.common.api.PatchValue;
import com.example.laive.streamer.domain.StreamerAccount;
import com.example.laive.streamer.domain.StreamerProfile;
import com.example.laive.streamer.domain.UpdateStreamerProfileCommand;
import com.example.laive.streamer.exception.StreamerProfileException;
import com.example.laive.streamer.repository.StreamerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 부분 수정을 담당한다 (API 6.6).
 *
 * <p>PatchValue라 jakarta 애노테이션이 안쪽 값에 닿지 않으므로 값 검증은 여기서 한다.
 */
@Service
@Transactional
public class StreamerProfileCommandService {

    private static final int NICKNAME_MAX_LENGTH = 50;
    private static final int PROFILE_IMAGE_URL_MAX_LENGTH = 1024;
    private static final int BIO_MAX_LENGTH = 500;

    private final StreamerProfileRepository repository;
    private final StreamerTagService tagService;
    private final StreamerProfileQueryService queryService;

    public StreamerProfileCommandService(
            StreamerProfileRepository repository,
            StreamerTagService tagService,
            StreamerProfileQueryService queryService
    ) {
        this.repository = repository;
        this.tagService = tagService;
        this.queryService = queryService;
    }

    /** 보낸 필드만 반영하고, 수정된 전체 프로필을 돌려준다. */
    public StreamerProfile update(long streamerId, UpdateStreamerProfileCommand command) {
        if (command.isEmpty()) {
            throw StreamerProfileException.emptyPatch();
        }

        StreamerAccount current = repository.findAccount(streamerId)
                .orElseThrow(StreamerProfileException::notFound);

        String nickname = normalizeNickname(
                PatchValue.resolve(command.nickname(), current.nickname()));
        String profileImageUrl = normalizeProfileImageUrl(
                PatchValue.resolve(command.profileImageUrl(), current.profileImageUrl()));
        String bio = normalizeBio(
                PatchValue.resolve(command.bio(), current.bio()));

        repository.updateProfile(streamerId, nickname, profileImageUrl, bio);

        if (command.tagIds() != null) {
            tagService.replaceTags(streamerId, command.tagIds().value());
        }

        return queryService.getProfile(streamerId);
    }

    /** 닉네임은 비울 수 없는 NOT NULL 필드다. 명시적 null·공백도 거절한다. */
    private String normalizeNickname(String value) {
        if (value == null || value.isBlank()) {
            throw StreamerProfileException.invalidField("닉네임은 비울 수 없습니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > NICKNAME_MAX_LENGTH) {
            throw StreamerProfileException.invalidField(
                    "닉네임은 " + NICKNAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private String normalizeProfileImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > PROFILE_IMAGE_URL_MAX_LENGTH) {
            throw StreamerProfileException.invalidField("프로필 이미지 URL이 너무 깁니다.");
        }
        return normalized;
    }

    private String normalizeBio(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > BIO_MAX_LENGTH) {
            throw StreamerProfileException.invalidField(
                    "소개는 " + BIO_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return normalized;
    }
}

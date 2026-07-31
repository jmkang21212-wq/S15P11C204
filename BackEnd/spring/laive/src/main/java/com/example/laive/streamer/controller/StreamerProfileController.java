package com.example.laive.streamer.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.streamer.dto.request.UpdateStreamerProfileRequest;
import com.example.laive.streamer.dto.response.StreamerProfileResponse;
import com.example.laive.streamer.service.StreamerProfileCommandService;
import com.example.laive.streamer.service.StreamerProfileQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 스트리머 본인의 프로필 API다 (명세 6.6).
 *
 * <p>여기서 고른 태그가 포트폴리오(명세 12)의 tags·태그 검색에 그대로 이어진다.
 * 포트폴리오는 프로필 데이터를 복사하지 않고 조회 시 조인한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/streamers")
public class StreamerProfileController {

    private final StreamerProfileQueryService queryService;
    private final StreamerProfileCommandService commandService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public StreamerProfileController(
            StreamerProfileQueryService queryService,
            StreamerProfileCommandService commandService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StreamerProfileResponse>> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(StreamerProfileResponse.from(
                queryService.getProfile(streamerIdExtractor.extract(jwt)))));
    }

    /** 보낸 필드만 수정한다. 수정 가능 필드는 nickname·profileImageUrl·bio·tagIds다. */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<StreamerProfileResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStreamerProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(StreamerProfileResponse.from(
                commandService.update(streamerIdExtractor.extract(jwt), request.toCommand()))));
    }
}

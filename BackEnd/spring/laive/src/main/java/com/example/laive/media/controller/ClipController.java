package com.example.laive.media.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.media.dto.request.ClipFailureRequest;
import com.example.laive.media.dto.response.ClipJobsResponse;
import com.example.laive.media.dto.response.ClipUploadResponse;
import com.example.laive.media.service.ClipJobService;
import com.example.laive.media.service.ClipUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 클립 작업 조회와 결과 업로드 API다(CLIP_SPEC.md §6.4~6.6).
 *
 * <p>서버가 작업을 밀어주지 않고 앱이 가져가는 구조다. 스트리머가 방송 종료 직후
 * 앱을 끄는 것이 정상 행동이라, push로 밀면 그 순간 작업이 증발한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class ClipController {

    private final ClipJobService jobService;
    private final ClipUploadService uploadService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public ClipController(
            ClipJobService jobService,
            ClipUploadService uploadService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.jobService = jobService;
        this.uploadService = uploadService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /** 아직 클립이 없는 하이라이트를 녹화본이 준비된 것만 골라 준다. 앱 시작 시 호출한다. */
    @GetMapping("/clip-jobs")
    public ResponseEntity<ApiResponse<ClipJobsResponse>> jobs(@AuthenticationPrincipal Jwt jwt) {
        var jobs = jobService.findPending(streamerIdExtractor.extract(jwt));
        return ResponseEntity.ok(ApiResponse.success(ClipJobsResponse.from(jobs)));
    }

    /**
     * 클립과 썸네일을 올린다. 이미 올라간 하이라이트면 기존 URL로 200을 돌려준다 —
     * 저장은 됐는데 응답을 못 받은 재시도가 정상 흐름이다.
     */
    @PostMapping(path = "/highlights/{highlightId}/clip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ClipUploadResponse>> upload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long highlightId,
            @RequestPart("clip") MultipartFile clip,
            @RequestPart("thumbnail") MultipartFile thumbnail
    ) {
        var uploaded = uploadService.upload(
                streamerIdExtractor.extract(jwt), highlightId, clip, thumbnail);
        return ResponseEntity.ok(ApiResponse.success(ClipUploadResponse.from(uploaded)));
    }

    /**
     * 클립을 만들지 못했음을 보고한다. 이걸 안 보내면 서버가 영원히 PENDING으로 들고
     * 있다가 앱이 켜질 때마다 같은 작업을 다시 준다.
     */
    @PostMapping("/highlights/{highlightId}/clip/failure")
    public ResponseEntity<Void> fail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long highlightId,
            @Valid @RequestBody ClipFailureRequest request
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        if (request.permanent()) {
            uploadService.skip(streamerId, highlightId, request.reason());
        } else {
            uploadService.fail(streamerId, highlightId, request.reason());
        }
        return ResponseEntity.noContent().build();
    }
}

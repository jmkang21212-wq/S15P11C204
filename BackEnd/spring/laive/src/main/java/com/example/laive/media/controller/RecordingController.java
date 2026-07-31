package com.example.laive.media.controller;

import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.media.dto.request.FinishRecordingRequest;
import com.example.laive.media.dto.request.StartRecordingRequest;
import com.example.laive.media.dto.request.UpdateRecordingBaseRequest;
import com.example.laive.media.service.RecordingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Electron이 OBS 로컬 녹화 상태를 보고하는 API다(CLIP_SPEC.md §6.1~6.3).
 *
 * <p>{@code /internal/**}의 서비스 토큰이 아니라 사용자 JWT를 쓴다 — 호출자가
 * 스트리머 본인의 앱이기 때문이다. 남의 세션에 보고할 수 없도록 서비스가
 * 소유권을 확인한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/broadcast-sessions/{sessionId}/recording")
public class RecordingController {

    private final RecordingService service;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public RecordingController(RecordingService service, JwtStreamerIdExtractor streamerIdExtractor) {
        this.service = service;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /** 녹화 시작. 같은 세션에 두 번 와도 409를 내지 않는다 — 앱의 재시도가 정상 흐름이다. */
    @PostMapping
    public ResponseEntity<Void> start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody StartRecordingRequest request
    ) {
        service.start(
                streamerIdExtractor.extract(jwt), sessionId,
                request.startedAt(), request.baseWallClock(), request.baseTimecodeMs()
        );
        return ResponseEntity.noContent().build();
    }

    /** 시간 기준점 갱신(60초 주기). 드리프트 누적을 없애는 유일한 수단이다. */
    @PatchMapping("/base")
    public ResponseEntity<Void> updateBase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody UpdateRecordingBaseRequest request
    ) {
        service.updateBase(
                streamerIdExtractor.extract(jwt), sessionId,
                request.baseWallClock(), request.baseTimecodeMs()
        );
        return ResponseEntity.noContent().build();
    }

    /** 녹화 종료. 파일 경로가 확정되어야 그 세션의 클립 작업이 생긴다. */
    @PatchMapping
    public ResponseEntity<Void> finish(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody FinishRecordingRequest request
    ) {
        service.finish(
                streamerIdExtractor.extract(jwt), sessionId,
                request.endedAt(), request.outputPath(), request.status()
        );
        return ResponseEntity.noContent().build();
    }
}

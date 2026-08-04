package com.example.laive.broadcast.live.controller;

import com.example.laive.broadcast.live.dto.response.LiveSnapshotResponse;
import com.example.laive.broadcast.live.service.LiveSnapshotService;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 콘솔용 API다(명세 10.6).
 *
 * <p>{@code BroadcastSessionController}와 분리한 이유: 저쪽은 세션 수명주기
 * CRUD고, 이쪽은 Redis 현재값 취합이라 의존성 집합이 전혀 다르다.
 *
 * <p>전달 방식은 폴링이다(2026-07-29 결정). 프론트는 이 스냅샷을 2~3초 주기로
 * 조회해 콘솔 화면 전체를 갱신한다. WebSocket(명세 10.7)은 후순위로 남겨두었다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/broadcast-sessions")
public class LiveConsoleController {

    private final LiveSnapshotService snapshotService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public LiveConsoleController(
            LiveSnapshotService snapshotService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.snapshotService = snapshotService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /** 콘솔 화면 상태를 한 번에 내려준다. 새로고침·재입장 복구도 이 응답 하나로 끝난다. */
    @GetMapping("/{sessionId}/live-snapshot")
    public ResponseEntity<ApiResponse<LiveSnapshotResponse>> liveSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        return ResponseEntity.ok(ApiResponse.success(snapshotService.getSnapshot(streamerId, sessionId)));
    }
}

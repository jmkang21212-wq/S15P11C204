package com.example.laive.broadcast.summary.controller;

import com.example.laive.broadcast.summary.dto.response.ChatSummaryCreateResponse;
import com.example.laive.broadcast.summary.service.ChatSummaryCommandService;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실시간 콘솔의 수동 여론 요약 버튼이다. 생성된 요약 자체는 브리핑으로 저장되어
 * live-snapshot(10.6)의 briefings에도 실려 나간다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/broadcast-sessions/{sessionId}/chat-summary")
public class ChatSummaryController {

    private final ChatSummaryCommandService commandService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public ChatSummaryController(
            ChatSummaryCommandService commandService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.commandService = commandService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /**
     * 여론 요약을 즉시 생성한다(계약 §2의 MANUAL). 쿨다운·생성 중·채팅 부족은
     * 오류가 아니라 200의 status로 내려간다 — 프론트는 nextAvailableAt으로 버튼을
     * 비활성화하고 카운트다운을 그린다.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatSummaryCreateResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        return ResponseEntity.ok(ApiResponse.success(commandService.create(streamerId, sessionId)));
    }
}

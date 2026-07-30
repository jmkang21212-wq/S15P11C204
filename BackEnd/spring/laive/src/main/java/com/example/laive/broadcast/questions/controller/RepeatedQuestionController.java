package com.example.laive.broadcast.questions.controller;

import com.example.laive.broadcast.questions.dto.response.RepeatedQuestionDismissResponse;
import com.example.laive.broadcast.questions.service.RepeatedQuestionCommandService;
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
 * 실시간 콘솔의 반복 질문 조작이다. 목록 자체는 live-snapshot(10.6)에 실려 나간다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/broadcast-sessions/{sessionId}/repeated-questions")
public class RepeatedQuestionController {

    private final RepeatedQuestionCommandService commandService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public RepeatedQuestionController(
            RepeatedQuestionCommandService commandService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.commandService = commandService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /**
     * 답변 완료 처리. 콘솔에서 사라지고, 이후 같은 질문이 임계 이상 다시 늘면
     * 재질문으로 다시 노출된다. 멱등 — 중복 클릭에 안전하다.
     */
    @PostMapping("/{questionId}/dismiss")
    public ResponseEntity<ApiResponse<RepeatedQuestionDismissResponse>> dismiss(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId,
            @PathVariable @Positive long questionId
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        return ResponseEntity.ok(ApiResponse.success(commandService.dismiss(streamerId, sessionId, questionId)));
    }
}

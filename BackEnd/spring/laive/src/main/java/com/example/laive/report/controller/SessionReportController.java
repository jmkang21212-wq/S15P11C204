package com.example.laive.report.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.media.storage.ClipUrlResolver;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.dto.response.HighlightResponse;
import com.example.laive.report.dto.response.SessionReportProcessingResponse;
import com.example.laive.report.dto.response.SessionReportResponse;
import com.example.laive.report.service.ReportQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/broadcast-sessions/{sessionId}")
public class SessionReportController {
    private final ReportQueryService service;
    private final JwtStreamerIdExtractor streamerIdExtractor;
    private final ClipUrlResolver clipUrlResolver;

    public SessionReportController(
            ReportQueryService service,
            JwtStreamerIdExtractor streamerIdExtractor,
            ClipUrlResolver clipUrlResolver
    ) {
        this.service = service;
        this.streamerIdExtractor = streamerIdExtractor;
        this.clipUrlResolver = clipUrlResolver;
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<?>> report(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        var detail = service.get(streamerIdExtractor.extract(jwt), sessionId);
        if (detail.report().status() == ReportStatus.PROCESSING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(SessionReportProcessingResponse.from(detail)));
        }
        return ResponseEntity.ok(ApiResponse.success(
                SessionReportResponse.from(detail, clipUrlResolver)));
    }

    @GetMapping("/highlights")
    public ResponseEntity<ApiResponse<List<HighlightResponse>>> highlights(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long sessionId
    ) {
        var response = service.getHighlights(streamerIdExtractor.extract(jwt), sessionId)
                .stream().map(highlight -> HighlightResponse.from(highlight, clipUrlResolver)).toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

package com.example.laive.dashboard.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.dashboard.dto.response.DashboardResponse;
import com.example.laive.dashboard.service.DashboardQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardQueryService service;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public DashboardController(
            DashboardQueryService service,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.service = service;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> get(
            @AuthenticationPrincipal Jwt jwt
    ) {
        var dashboard = service.get(streamerIdExtractor.extract(jwt));
        return ResponseEntity.ok(ApiResponse.success(DashboardResponse.from(dashboard)));
    }
}

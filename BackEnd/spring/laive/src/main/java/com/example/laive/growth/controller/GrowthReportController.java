package com.example.laive.growth.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.dto.response.GrowthReportResponse;
import com.example.laive.growth.service.GrowthReportQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/growth-reports")
public class GrowthReportController {
    private final GrowthReportQueryService service;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public GrowthReportController(
            GrowthReportQueryService service,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.service = service;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GrowthReportResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam GrowthPeriodType periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var report = service.getReport(
                streamerIdExtractor.extract(jwt), periodType, from, to
        );
        return ResponseEntity.ok(ApiResponse.success(GrowthReportResponse.from(report)));
    }
}

package com.example.laive.report.controller;

import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.media.config.ClipProperties;
import com.example.laive.media.storage.ClipUrlResolver;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.SentimentCounts;
import com.example.laive.report.domain.SessionReport;
import com.example.laive.report.domain.SessionReportDetail;
import com.example.laive.report.service.ReportQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionReportControllerTest {
    private MockMvc mockMvc;
    private ReportQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(ReportQueryService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        when(extractor.extract(any())).thenReturn(101L);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("101")
                .issuedAt(Instant.parse("2026-07-20T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-20T01:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SessionReportController(service, extractor, clipUrlResolver()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 클립 키를 절대 URL로 바꾸는 실제 동작을 그대로 태운다 — 조립 규칙까지 응답에서 검증한다. */
    private ClipUrlResolver clipUrlResolver() {
        return new ClipUrlResolver(new ClipProperties(
                Duration.ofMillis(4_500), 3, "/tmp/clips", "https://laive.test/clips", 200
        ));
    }

    @Test
    void processingReportReturnsAccepted() throws Exception {
        when(service.get(101L, 9001L)).thenReturn(detail(ReportStatus.PROCESSING, 0.7));

        mockMvc.perform(get("/api/v1/broadcast-sessions/9001/report"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.progress").value(0.7));
    }

    @Test
    void completedReportReturnsEightSentimentsAndEmptyCollections() throws Exception {
        when(service.get(101L, 9001L)).thenReturn(detail(ReportStatus.COMPLETED, 1.0));

        mockMvc.perform(get("/api/v1/broadcast-sessions/9001/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.dominant").value("NEUTRAL"))
                .andExpect(jsonPath("$.data.metrics.distribution.NEUTRAL").value(0.0))
                .andExpect(jsonPath("$.data.triggers").isArray())
                .andExpect(jsonPath("$.data.highlights").isArray());
    }

    @Test
    void highlightsReturnsEmptyArray() throws Exception {
        when(service.getHighlights(101L, 9001L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/broadcast-sessions/9001/highlights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private SessionReportDetail detail(ReportStatus status, double progress) {
        var report = new SessionReport(
                301, 9001, "방송", Instant.parse("2026-07-20T13:00:00Z"),
                Instant.parse("2026-07-20T14:00:00Z"), status,
                status == ReportStatus.COMPLETED ? "요약" : null,
                0, 0, 0,
                new SentimentCounts(0, 0, 0, 0, 0, 0, 0, 0, 0),
                status == ReportStatus.COMPLETED ? Instant.parse("2026-07-20T14:01:00Z") : null
        );
        return new SessionReportDetail(report, List.of(), List.of(), List.of(), List.of(), progress);
    }
}

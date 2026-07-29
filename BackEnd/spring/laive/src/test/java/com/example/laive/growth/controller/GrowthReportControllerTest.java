package com.example.laive.growth.controller;

import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.domain.GrowthReport;
import com.example.laive.growth.domain.GrowthSeriesPoint;
import com.example.laive.growth.domain.GrowthSummary;
import com.example.laive.growth.service.GrowthReportQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GrowthReportControllerTest {
    private MockMvc mockMvc;
    private GrowthReportQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(GrowthReportQueryService.class);
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
                .standaloneSetup(new GrowthReportController(service, extractor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsZeroForUnavailableAverageAndChangeRate() throws Exception {
        LocalDate from = LocalDate.parse("2026-07-13");
        LocalDate to = LocalDate.parse("2026-07-19");
        when(service.getReport(101L, GrowthPeriodType.WEEKLY, from, to))
                .thenReturn(new GrowthReport(
                        GrowthPeriodType.WEEKLY,
                        from,
                        to,
                        new GrowthSummary(0, java.math.BigDecimal.ZERO, 0,
                                java.math.BigDecimal.ZERO, 0, java.math.BigDecimal.ZERO),
                        List.of(new GrowthSeriesPoint(from, to, 0, 0, 0))
                ));

        mockMvc.perform(get("/api/v1/growth-reports")
                        .queryParam("periodType", "WEEKLY")
                        .queryParam("from", "2026-07-13")
                        .queryParam("to", "2026-07-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summaryMetrics.avgViewers").value(0))
                .andExpect(jsonPath("$.data.summaryMetrics.avgViewersChangeRate").value(0))
                .andExpect(jsonPath("$.data.series[0].totalChat").value(0));
    }
}

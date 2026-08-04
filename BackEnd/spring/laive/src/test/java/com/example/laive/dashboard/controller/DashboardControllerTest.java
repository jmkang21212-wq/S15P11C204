package com.example.laive.dashboard.controller;

import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.dashboard.domain.Dashboard;
import com.example.laive.dashboard.service.DashboardQueryService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {
    private MockMvc mockMvc;
    private DashboardQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(DashboardQueryService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        when(extractor.extract(any())).thenReturn(101L);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256").subject("101")
                .issuedAt(Instant.parse("2026-07-30T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-30T01:00:00Z")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(service, extractor))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsStableEmptyDashboardEnvelope() throws Exception {
        when(service.get(101L)).thenReturn(new Dashboard(
                null, null, 0, List.of(), null, List.of()
        ));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.upcomingBroadcast").doesNotExist())
                .andExpect(jsonPath("$.data.pendingPromiseCount").value(0))
                .andExpect(jsonPath("$.data.pendingPromises").isEmpty())
                .andExpect(jsonPath("$.data.recentHighlights").isEmpty());
    }
}

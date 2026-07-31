package com.example.laive.growth.roadmap.controller;

import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.growth.roadmap.domain.GrowthRoadmapGeneration;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationStatus;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationTrigger;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.service.GrowthRoadmapQueryService;
import com.example.laive.growth.roadmap.service.GrowthRoadmapTransactionService;
import com.example.laive.growth.roadmap.service.GrowthSuggestionCommandService;
import com.example.laive.growth.service.GrowthGoalQueryService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GrowthRoadmapControllerTest {
    private MockMvc mockMvc;
    private GrowthRoadmapQueryService queryService;
    private GrowthRoadmapTransactionService transactions;
    private GrowthSuggestionCommandService suggestionCommands;

    @BeforeEach
    void setUp() {
        queryService = mock(GrowthRoadmapQueryService.class);
        transactions = mock(GrowthRoadmapTransactionService.class);
        suggestionCommands = mock(GrowthSuggestionCommandService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        when(extractor.extract(any())).thenReturn(101L);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("101")
                .issuedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-01T01:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new GrowthRoadmapController(
                        queryService,
                        transactions,
                        suggestionCommands,
                        mock(GrowthGoalQueryService.class),
                        extractor
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsNotRequestedInsteadOf404WhenRoadmapDoesNotExist() throws Exception {
        when(queryService.find(101L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/growth-roadmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.suggestions").isEmpty());
    }

    @Test
    void returnsAcceptedGenerationForManualRegeneration() throws Exception {
        Instant requestedAt = Instant.parse("2026-08-01T00:00:00Z");
        when(transactions.requestManual(101L)).thenReturn(
                new GrowthRoadmapGeneration(
                        301L, 41L, null, RoadmapGenerationTrigger.MANUAL,
                        RoadmapGenerationStatus.PENDING, RoadmapQuality.ESTABLISHED,
                        "growth-roadmap-v1", "manual-key", null,
                        requestedAt, null, null, null
                )
        );

        mockMvc.perform(post("/api/v1/growth-roadmap/regenerate"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generationId").value(301))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void dismissesOwnedSuggestionWithoutResponseBody() throws Exception {
        mockMvc.perform(post("/api/v1/growth-roadmap/suggestions/77/dismiss"))
                .andExpect(status().isNoContent());

        verify(suggestionCommands).dismiss(101L, 77L);
    }
}

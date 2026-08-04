package com.example.laive.broadcast.questions.controller;

import com.example.laive.broadcast.questions.dto.response.RepeatedQuestionDismissResponse;
import com.example.laive.broadcast.questions.exception.RepeatedQuestionException;
import com.example.laive.broadcast.questions.service.RepeatedQuestionCommandService;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepeatedQuestionControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final long STREAMER_ID = 101L;
    private static final long QUESTION_ID = 1753690000000007L;

    private RepeatedQuestionCommandService commandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(RepeatedQuestionCommandService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(Long.toString(STREAMER_ID))
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(1800))
                .build();
        when(extractor.extract(any())).thenReturn(STREAMER_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        RepeatedQuestionController controller = new RepeatedQuestionController(commandService, extractor);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dismissesQuestion() throws Exception {
        when(commandService.dismiss(STREAMER_ID, 9002L, QUESTION_ID))
                .thenReturn(new RepeatedQuestionDismissResponse(QUESTION_ID, NOW));

        mockMvc.perform(post(
                        "/api/v1/broadcast-sessions/9002/repeated-questions/{questionId}/dismiss", QUESTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionId").value(QUESTION_ID))
                .andExpect(jsonPath("$.data.dismissedAt").value("2026-07-30T12:00:00Z"));
    }

    @Test
    void unknownQuestionIsNotFound() throws Exception {
        when(commandService.dismiss(STREAMER_ID, 9002L, QUESTION_ID))
                .thenThrow(RepeatedQuestionException.notFound());

        mockMvc.perform(post(
                        "/api/v1/broadcast-sessions/9002/repeated-questions/{questionId}/dismiss", QUESTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void hidesOtherStreamersSessionAsNotFound() throws Exception {
        when(commandService.dismiss(STREAMER_ID, 9003L, QUESTION_ID))
                .thenThrow(BroadcastSessionException.notFound());

        mockMvc.perform(post(
                        "/api/v1/broadcast-sessions/9003/repeated-questions/{questionId}/dismiss", QUESTION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

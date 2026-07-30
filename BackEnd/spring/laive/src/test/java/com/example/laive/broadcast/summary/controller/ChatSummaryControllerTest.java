package com.example.laive.broadcast.summary.controller;

import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.broadcast.summary.dto.response.ChatSummaryCreateResponse;
import com.example.laive.broadcast.summary.exception.ChatSummaryException;
import com.example.laive.broadcast.summary.service.ChatSummaryCommandService;
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

class ChatSummaryControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final long STREAMER_ID = 101L;

    private ChatSummaryCommandService commandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(ChatSummaryCommandService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(Long.toString(STREAMER_ID))
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(1800))
                .build();
        when(extractor.extract(any())).thenReturn(STREAMER_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ChatSummaryController controller = new ChatSummaryController(commandService, extractor);
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
    void createdCarriesSummaryAndCountdownAnchor() throws Exception {
        when(commandService.create(STREAMER_ID, 9002L)).thenReturn(
                new ChatSummaryCreateResponse(
                        "CREATED", "환호가 집중되고 있습니다.", NOW, NOW.plusSeconds(60),
                        null, null, null));

        mockMvc.perform(post("/api/v1/broadcast-sessions/9002/chat-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.summary").value("환호가 집중되고 있습니다."))
                .andExpect(jsonPath("$.data.generatedAt").value("2026-07-30T12:00:00Z"))
                .andExpect(jsonPath("$.data.nextAvailableAt").value("2026-07-30T12:01:00Z"))
                // 해당 없는 필드는 내려가지 않는다(@JsonInclude NON_NULL).
                .andExpect(jsonPath("$.data.retryAfterSec").doesNotExist());
    }

    @Test
    void cooldownIsNormalResultNotError() throws Exception {
        when(commandService.create(STREAMER_ID, 9002L))
                .thenReturn(ChatSummaryCreateResponse.cooldown(42, NOW.plusSeconds(42)));

        mockMvc.perform(post("/api/v1/broadcast-sessions/9002/chat-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COOLDOWN"))
                .andExpect(jsonPath("$.data.retryAfterSec").value(42))
                .andExpect(jsonPath("$.data.nextAvailableAt").value("2026-07-30T12:00:42Z"))
                .andExpect(jsonPath("$.data.summary").doesNotExist());
    }

    @Test
    void notEnoughChatsCarriesCounts() throws Exception {
        when(commandService.create(STREAMER_ID, 9002L))
                .thenReturn(ChatSummaryCreateResponse.notEnoughChats(12, 30));

        mockMvc.perform(post("/api/v1/broadcast-sessions/9002/chat-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_ENOUGH_CHATS"))
                .andExpect(jsonPath("$.data.chatCount").value(12))
                .andExpect(jsonPath("$.data.required").value(30));
    }

    @Test
    void aiUnavailableIsServiceUnavailable() throws Exception {
        when(commandService.create(STREAMER_ID, 9002L))
                .thenThrow(ChatSummaryException.unavailable());

        mockMvc.perform(post("/api/v1/broadcast-sessions/9002/chat-summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMARY_UNAVAILABLE"));
    }

    @Test
    void hidesOtherStreamersSessionAsNotFound() throws Exception {
        when(commandService.create(STREAMER_ID, 9003L))
                .thenThrow(BroadcastSessionException.notFound());

        mockMvc.perform(post("/api/v1/broadcast-sessions/9003/chat-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

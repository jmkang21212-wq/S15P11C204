package com.example.laive.broadcast.session.controller;

import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.EndBroadcastSessionResult;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;
import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.service.BroadcastSessionCommandService;
import com.example.laive.broadcast.session.service.BroadcastSessionQueryService;
import com.example.laive.broadcast.session.service.VoiceTicketService;
import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BroadcastSessionControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private static final long STREAMER_ID = 101L;

    private BroadcastSessionCommandService commandService;
    private BroadcastSessionQueryService queryService;
    private VoiceTicketService voiceTicketService;
    private Jwt jwt;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(BroadcastSessionCommandService.class);
        queryService = mock(BroadcastSessionQueryService.class);
        voiceTicketService = mock(VoiceTicketService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(Long.toString(STREAMER_ID))
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(1800))
                .build();
        when(extractor.extract(any())).thenReturn(STREAMER_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        BroadcastSessionController controller = new BroadcastSessionController(
                commandService,
                queryService,
                voiceTicketService,
                new BroadcastSessionProperties(
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(60),
                        new BroadcastSessionProperties.Outbox(
                                500L, 50, Duration.ofSeconds(30), 8, Duration.ofMinutes(1))
                ),
                extractor
        );
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
    void startsPreparingSession() throws Exception {
        when(commandService.start(eq(STREAMER_ID), any()))
                .thenReturn(new StartBroadcastSessionResult(
                        9002L,
                        BroadcastStatus.PREPARING,
                        NOW
                ));

        mockMvc.perform(post("/api/v1/broadcast-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformAccountId": 15,
                                  "title": "방송",
                                  "category": "FPS",
                                  "language": "ko"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value(9002))
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.startedAt").value("2026-07-21T12:00:00Z"));
    }

    @Test
    void returnsNoContentWhenCurrentSessionDoesNotExist() throws Exception {
        when(queryService.getCurrent(STREAMER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/broadcast-sessions/current")
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void endsSessionAsAccepted() throws Exception {
        when(commandService.end(STREAMER_ID, 9002L))
                .thenReturn(new EndBroadcastSessionResult(
                        9002L,
                        BroadcastStatus.ENDED,
                        "PROCESSING"
                ));

        mockMvc.perform(post("/api/v1/broadcast-sessions/9002/end")
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.reportStatus").value("PROCESSING"));
    }

    @Test
    void returnsCurrentSession() throws Exception {
        when(queryService.getCurrent(STREAMER_ID)).thenReturn(Optional.of(
                new BroadcastSessionDetail(
                        9002L,
                        15L,
                        null,
                        "방송",
                        "FPS",
                        "ko",
                        null,
                        null,
                        BroadcastStatus.LIVE,
                        NOW,
                        null
                )
        ));

        mockMvc.perform(get("/api/v1/broadcast-sessions/current")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(9002))
                .andExpect(jsonPath("$.data.status").value("LIVE"));
    }
}

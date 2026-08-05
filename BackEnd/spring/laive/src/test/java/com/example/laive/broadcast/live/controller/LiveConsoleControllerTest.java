package com.example.laive.broadcast.live.controller;

import com.example.laive.broadcast.live.dto.response.LiveSnapshotResponse;
import com.example.laive.broadcast.live.service.LiveSnapshotService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveConsoleControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private static final long STREAMER_ID = 101L;

    private LiveSnapshotService snapshotService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        snapshotService = mock(LiveSnapshotService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(Long.toString(STREAMER_ID))
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(1800))
                .build();
        when(extractor.extract(any())).thenReturn(STREAMER_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        LiveConsoleController controller = new LiveConsoleController(snapshotService, extractor);
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
    void returnsSnapshotShapedBySpec() throws Exception {
        when(snapshotService.getSnapshot(STREAMER_ID, 9002L)).thenReturn(new LiveSnapshotResponse(
                new LiveSnapshotResponse.Session(9002L, "LIVE", NOW, 6452L, 2417),
                new LiveSnapshotResponse.Services("DISCONNECTED", "STOPPED", "RUNNING", "RUNNING"),
                List.of(),
                new LiveSnapshotResponse.Sentiment(
                        180, 6185L, 0.214, "JOY",
                        Map.of("JOY", 0.27, "NEUTRAL", 0.25)
                ),
                new LiveSnapshotResponse.Highlight(
                        "ACTIVE", 4.2, 3.0, 0.31, 57, 41, null,
                        NOW.minusSeconds(6000), 60, 2, NOW.minusSeconds(120)
                ),
                List.of(new LiveSnapshotResponse.RepeatedQuestion(
                        1753690000000007L, "오늘 몇 시까지 하세요?", "FAQ", 42, 51, true,
                        NOW.minusSeconds(600), NOW.minusSeconds(30)
                )),
                List.of(new LiveSnapshotResponse.TopViewer(
                        77L, "한별팬1호", "viewer-a91c", 42, 35, 112.0
                )),
                "1753089600123-0"
        ));

        mockMvc.perform(get("/api/v1/broadcast-sessions/9002/live-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.session.sessionId").value(9002))
                .andExpect(jsonPath("$.data.session.status").value("LIVE"))
                .andExpect(jsonPath("$.data.session.currentViewers").value(2417))
                .andExpect(jsonPath("$.data.services.collector").value("RUNNING"))
                .andExpect(jsonPath("$.data.services.microphone").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.sentiment.analyzedChat").value(6185))
                .andExpect(jsonPath("$.data.sentiment.dominant").value("JOY"))
                .andExpect(jsonPath("$.data.sentiment.distribution.JOY").value(0.27))
                .andExpect(jsonPath("$.data.highlight.phase").value("ACTIVE"))
                .andExpect(jsonPath("$.data.highlight.z").value(4.2))
                .andExpect(jsonPath("$.data.highlight.detectedCount").value(2))
                .andExpect(jsonPath("$.data.topViewers[0].nickname").value("한별팬1호"))
                .andExpect(jsonPath("$.data.topViewers[0].viewerAlias").value("viewer-a91c"))
                .andExpect(jsonPath("$.data.topViewers[0].totalScore").value(112.0))
                .andExpect(jsonPath("$.data.briefings").isEmpty())
                .andExpect(jsonPath("$.data.repeatedQuestions[0].questionId").value(1753690000000007L))
                .andExpect(jsonPath("$.data.repeatedQuestions[0].text").value("오늘 몇 시까지 하세요?"))
                .andExpect(jsonPath("$.data.repeatedQuestions[0].type").value("FAQ"))
                .andExpect(jsonPath("$.data.repeatedQuestions[0].askers").value(42))
                .andExpect(jsonPath("$.data.repeatedQuestions[0].messages").value(51))
                .andExpect(jsonPath("$.data.repeatedQuestions[0].reasked").value(true))
                .andExpect(jsonPath("$.data.lastEventId").value("1753089600123-0"));
    }

    @Test
    void hidesOtherStreamersSessionAsNotFound() throws Exception {
        when(snapshotService.getSnapshot(STREAMER_ID, 9003L))
                .thenThrow(BroadcastSessionException.notFound());

        mockMvc.perform(get("/api/v1/broadcast-sessions/9003/live-snapshot"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

package com.example.laive.media.controller;

import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.media.domain.RecordingStatus;
import com.example.laive.media.exception.MediaException;
import com.example.laive.media.service.RecordingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 녹화 보고 API의 URL 매핑·본문 바인딩·검증을 HTTP 경계에서 확인한다.
 *
 * <p>서비스 계층은 통합 테스트가 덮지만, 필드명이나 경로가 어긋나는 실수는 여기서만
 * 드러난다 — 프론트가 붙기 전에 계약을 고정하는 것이 목적이다.
 */
class RecordingControllerTest {

    private static final long STREAMER_ID = 101L;
    private static final long SESSION_ID = 8821L;

    private RecordingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RecordingService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        when(extractor.extract(any())).thenReturn(STREAMER_ID);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(String.valueOf(STREAMER_ID))
                .issuedAt(Instant.parse("2026-07-31T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-31T01:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecordingController(service, extractor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 녹화_시작_보고는_204를_준다() throws Exception {
        mockMvc.perform(post("/api/v1/broadcast-sessions/{id}/recording", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-07-31T20:00:03.120+09:00",
                                 "baseWallClock":"2026-07-31T20:00:03.120+09:00",
                                 "baseTimecodeMs":0}
                                """))
                .andExpect(status().isNoContent());

        verify(service).start(eq(STREAMER_ID), eq(SESSION_ID),
                eq(Instant.parse("2026-07-31T11:00:03.120Z")),
                eq(Instant.parse("2026-07-31T11:00:03.120Z")), eq(0L));
    }

    @Test
    void 기준점_갱신은_base_경로로_받는다() throws Exception {
        mockMvc.perform(patch("/api/v1/broadcast-sessions/{id}/recording/base", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseWallClock":"2026-07-31T21:15:03.400+09:00",
                                 "baseTimecodeMs":4500280}
                                """))
                .andExpect(status().isNoContent());

        verify(service).updateBase(eq(STREAMER_ID), eq(SESSION_ID),
                eq(Instant.parse("2026-07-31T12:15:03.400Z")), eq(4_500_280L));
    }

    @Test
    void 녹화_종료는_경로와_상태를_함께_받는다() throws Exception {
        mockMvc.perform(patch("/api/v1/broadcast-sessions/{id}/recording", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endedAt":"2026-07-31T23:02:11.000+09:00",
                                 "outputPath":"C:\\\\Videos\\\\rec.mkv",
                                 "status":"COMPLETED"}
                                """))
                .andExpect(status().isNoContent());

        verify(service).finish(eq(STREAMER_ID), eq(SESSION_ID),
                eq(Instant.parse("2026-07-31T14:02:11Z")),
                eq("C:\\Videos\\rec.mkv"), eq(RecordingStatus.COMPLETED));
    }

    @Test
    void 필수_필드가_빠지면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/broadcast-sessions/{id}/recording", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-07-31T20:00:03.120+09:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 남의_세션이면_404와_오류_코드를_준다() throws Exception {
        doThrow(MediaException.sessionNotFound())
                .when(service).start(anyLong(), anyLong(), any(), any(), anyLong());

        mockMvc.perform(post("/api/v1/broadcast-sessions/{id}/recording", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedAt":"2026-07-31T20:00:03.120+09:00",
                                 "baseWallClock":"2026-07-31T20:00:03.120+09:00",
                                 "baseTimecodeMs":0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECORDING_SESSION_NOT_FOUND"));
    }
}

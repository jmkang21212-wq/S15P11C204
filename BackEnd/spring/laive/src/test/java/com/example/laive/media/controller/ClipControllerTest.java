package com.example.laive.media.controller;

import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.media.domain.ClipCut;
import com.example.laive.media.domain.ClipJob;
import com.example.laive.media.exception.MediaException;
import com.example.laive.media.service.ClipJobService;
import com.example.laive.media.service.ClipUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클립 조회·업로드 API의 HTTP 계약을 고정한다.
 *
 * <p>특히 multipart 파트 이름({@code clip}, {@code thumbnail})은 오타 하나로 조용히
 * 깨지고 앱 쪽에서는 원인 없는 400으로만 보인다. 프론트에 문서를 넘기기 전에
 * 여기서 실제로 바인딩되는지 확인한다.
 */
class ClipControllerTest {

    private static final long STREAMER_ID = 101L;
    private static final long HIGHLIGHT_ID = 4412L;

    private ClipJobService jobService;
    private ClipUploadService uploadService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jobService = mock(ClipJobService.class);
        uploadService = mock(ClipUploadService.class);
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        when(extractor.extract(any())).thenReturn(STREAMER_ID);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(String.valueOf(STREAMER_ID))
                .issuedAt(Instant.parse("2026-07-31T00:00:00Z"))
                .expiresAt(Instant.parse("2026-07-31T01:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClipController(jobService, uploadService, extractor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 작업_목록은_data_jobs_아래에_초_단위로_나온다() throws Exception {
        when(jobService.findPending(STREAMER_ID)).thenReturn(List.of(new ClipJob(
                HIGHLIGHT_ID, 8821L, "7월 31일 저녁 방송",
                "C:\\Videos\\rec.mkv", new ClipCut(4_495_500, 80_000)
        )));

        mockMvc.perform(get("/api/v1/clip-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobs[0].highlightId").value(HIGHLIGHT_ID))
                .andExpect(jsonPath("$.data.jobs[0].recordingPath").value("C:\\Videos\\rec.mkv"))
                .andExpect(jsonPath("$.data.jobs[0].clipStartSec").value(4495.5))
                .andExpect(jsonPath("$.data.jobs[0].clipDurationSec").value(80.0));
    }

    @Test
    void 작업이_없으면_빈_배열이다() throws Exception {
        when(jobService.findPending(STREAMER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clip-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isEmpty());
    }

    @Test
    void 업로드는_clip과_thumbnail_파트를_받아_URL을_준다() throws Exception {
        when(uploadService.upload(eq(STREAMER_ID), eq(HIGHLIGHT_ID), any(), any()))
                .thenReturn(new ClipUploadService.UploadedClip(
                        "https://laive.test/clips/8821/4412.mp4",
                        "https://laive.test/clips/8821/4412.jpg"
                ));

        mockMvc.perform(multipart("/api/v1/highlights/{id}/clip", HIGHLIGHT_ID)
                        .file(new MockMultipartFile("clip", "clip.mp4", "video/mp4", "v".getBytes()))
                        .file(new MockMultipartFile("thumbnail", "t.jpg", "image/jpeg", "i".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clipUrl").value("https://laive.test/clips/8821/4412.mp4"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://laive.test/clips/8821/4412.jpg"));
    }

    @Test
    void 파트_이름이_다르면_400이다() throws Exception {
        mockMvc.perform(multipart("/api/v1/highlights/{id}/clip", HIGHLIGHT_ID)
                        .file(new MockMultipartFile("video", "clip.mp4", "video/mp4", "v".getBytes()))
                        .file(new MockMultipartFile("thumbnail", "t.jpg", "image/jpeg", "i".getBytes())))
                .andExpect(status().isBadRequest());

        verify(uploadService, never()).upload(anyLong(), anyLong(), any(), any());
    }

    @Test
    void 업로드_거부는_오류_코드로_나간다() throws Exception {
        when(uploadService.upload(anyLong(), anyLong(), any(), any()))
                .thenThrow(MediaException.invalidFile());

        mockMvc.perform(multipart("/api/v1/highlights/{id}/clip", HIGHLIGHT_ID)
                        .file(new MockMultipartFile("clip", "clip.mp4", "text/plain", "v".getBytes()))
                        .file(new MockMultipartFile("thumbnail", "t.jpg", "image/jpeg", "i".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CLIP_INVALID_FILE"));
    }

    @Test
    void permanent가_false면_재시도_가능한_실패로_보고한다() throws Exception {
        mockMvc.perform(post("/api/v1/highlights/{id}/clip/failure", HIGHLIGHT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"ffmpeg exited with code 1","permanent":false}
                                """))
                .andExpect(status().isNoContent());

        verify(uploadService).fail(STREAMER_ID, HIGHLIGHT_ID, "ffmpeg exited with code 1");
        verify(uploadService, never()).skip(anyLong(), anyLong(), anyString());
    }

    @Test
    void permanent가_true면_즉시_포기로_보고한다() throws Exception {
        mockMvc.perform(post("/api/v1/highlights/{id}/clip/failure", HIGHLIGHT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"recording file not found","permanent":true}
                                """))
                .andExpect(status().isNoContent());

        verify(uploadService).skip(STREAMER_ID, HIGHLIGHT_ID, "recording file not found");
        verify(uploadService, never()).fail(anyLong(), anyLong(), anyString());
    }
}

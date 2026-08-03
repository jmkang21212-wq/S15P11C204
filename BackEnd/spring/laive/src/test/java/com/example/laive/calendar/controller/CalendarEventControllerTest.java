package com.example.laive.calendar.controller;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.service.CalendarEventCommandService;
import com.example.laive.calendar.service.CalendarEventQueryService;
import com.example.laive.common.exception.GlobalExceptionHandler;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.promise.service.PromiseQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalendarEventControllerTest {

    private static final long STREAMER_ID = 101L;
    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private static final Instant START = Instant.parse("2026-07-20T12:00:00Z");
    private static final Instant END = Instant.parse("2026-07-20T15:00:00Z");

    private CalendarEventCommandService commandService;
    private CalendarEventQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(CalendarEventCommandService.class);
        queryService = mock(CalendarEventQueryService.class);
        // 캘린더 조회는 기한이 잡힌 약속도 함께 싣는다. 이 테스트의 관심사가 아니라 빈 목록을 준다.
        PromiseQueryService promiseQueryService = mock(PromiseQueryService.class);
        when(promiseQueryService.getCalendarPromises(anyLong(), any(), any()))
                .thenReturn(List.of());
        JwtStreamerIdExtractor extractor = mock(JwtStreamerIdExtractor.class);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(Long.toString(STREAMER_ID))
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(1800))
                .build();
        when(extractor.extract(any())).thenReturn(STREAMER_ID);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalendarEventController(
                        commandService, queryService, promiseQueryService, extractor))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static CalendarEvent event(Long platformAccountId, CalendarEventType type, String memo) {
        return new CalendarEvent(
                44L,
                STREAMER_ID,
                platformAccountId,
                type,
                "발로란트 랭크 정복 Day 12",
                memo,
                START,
                END,
                CalendarEventStatus.SCHEDULED,
                null
        );
    }

    @Test
    void 기간_조회는_페이지_필드_없이_items만_반환한다() throws Exception {
        when(queryService.getEvents(eq(STREAMER_ID), any(), any())).thenReturn(List.of(
                event(15L, CalendarEventType.BROADCAST, "Day 11 이어서"),
                event(null, CalendarEventType.DAY_OFF, null)
        ));

        mockMvc.perform(get("/api/v1/calendar-events")
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-08-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].calendarEventId").value(44))
                .andExpect(jsonPath("$.data.items[0].eventType").value("BROADCAST"))
                .andExpect(jsonPath("$.data.items[0].startAt").value("2026-07-20T12:00:00Z"))
                .andExpect(jsonPath("$.data.items[1].platformAccountId").doesNotExist())
                .andExpect(jsonPath("$.data.page").doesNotExist());
    }

    @Test
    void from과_to가_없으면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/calendar-events"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 생성은_201과_생성된_일정을_반환한다() throws Exception {
        when(commandService.create(eq(STREAMER_ID), any()))
                .thenReturn(event(15L, CalendarEventType.BROADCAST, "Day 11 이어서"));

        mockMvc.perform(post("/api/v1/calendar-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformAccountId": 15,
                                  "eventType": "BROADCAST",
                                  "title": "발로란트 랭크 정복 Day 12",
                                  "memo": "Day 11 이어서",
                                  "startAt": "2026-07-20T12:00:00Z",
                                  "endAt": "2026-07-20T15:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.calendarEventId").value(44))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void 생성_요청에_제목이_없으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/calendar-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "DAY_OFF",
                                  "startAt": "2026-07-20T12:00:00Z",
                                  "endAt": "2026-07-20T15:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /**
     * PATCH 부분 수정의 핵심이다. 실제 HTTP·Jackson 경로에서 미전송과 명시적 null이
     * 서로 다른 Command로 전달되는지 확인한다.
     */
    @Test
    void 미전송_필드는_Command에서_null이고_명시적_null은_래퍼로_전달된다() throws Exception {
        when(commandService.update(eq(STREAMER_ID), eq(44L), any()))
                .thenReturn(event(15L, CalendarEventType.BROADCAST, null));

        mockMvc.perform(patch("/api/v1/calendar-events/44")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "memo": null, "title": "시간 변경" }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCalendarEventCommand> captor =
                ArgumentCaptor.forClass(UpdateCalendarEventCommand.class);
        verify(commandService).update(eq(STREAMER_ID), eq(44L), captor.capture());
        UpdateCalendarEventCommand command = captor.getValue();

        assertThat(command.memo()).isNotNull();
        assertThat(command.memo().value()).isNull();
        assertThat(command.title().value()).isEqualTo("시간 변경");
        assertThat(command.startAt()).isNull();
        assertThat(command.platformAccountId()).isNull();
        assertThat(command.status()).isNull();
    }

    @Test
    void PATCH_본문의_시각과_열거형이_타입대로_역직렬화된다() throws Exception {
        when(commandService.update(eq(STREAMER_ID), eq(44L), any()))
                .thenReturn(event(15L, CalendarEventType.BROADCAST, null));

        mockMvc.perform(patch("/api/v1/calendar-events/44")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startAt": "2026-07-20T13:00:00Z",
                                  "eventType": "DAY_OFF",
                                  "status": "CANCELLED"
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateCalendarEventCommand> captor =
                ArgumentCaptor.forClass(UpdateCalendarEventCommand.class);
        verify(commandService).update(eq(STREAMER_ID), eq(44L), captor.capture());
        UpdateCalendarEventCommand command = captor.getValue();

        assertThat(command.startAt().value()).isEqualTo(Instant.parse("2026-07-20T13:00:00Z"));
        assertThat(command.eventType().value()).isEqualTo(CalendarEventType.DAY_OFF);
        assertThat(command.status().value()).isEqualTo(CalendarEventStatus.CANCELLED);
    }

    @Test
    void 세션에_연결된_일정_수정_충돌은_409로_변환된다() throws Exception {
        when(commandService.update(eq(STREAMER_ID), eq(44L), any()))
                .thenThrow(CalendarEventException.linkedToSession());

        mockMvc.perform(patch("/api/v1/calendar-events/44")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "startAt": "2026-07-20T13:00:00Z" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CALENDAR_EVENT_LINKED_TO_SESSION"));
    }

    @Test
    void 없는_일정_조회는_404다() throws Exception {
        when(queryService.getEvent(eq(STREAMER_ID), anyLong()))
                .thenThrow(CalendarEventException.notFound());

        mockMvc.perform(get("/api/v1/calendar-events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CALENDAR_EVENT_NOT_FOUND"));
    }
}

package com.example.laive.calendar.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 조회 범위 제한(API 8.1)의 경계를 검증한다.
 */
class CalendarEventQueryServiceTest {

    private static final long STREAMER_ID = 101L;
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");

    private CalendarEventRepository repository;
    private CalendarEventQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarEventRepository.class);
        service = new CalendarEventQueryService(repository);
    }

    @Test
    void 정확히_62일은_허용한다() {
        Instant to = FROM.plus(Duration.ofDays(62));
        when(repository.findRange(STREAMER_ID, FROM, to)).thenReturn(List.of());

        assertThatCode(() -> service.getEvents(STREAMER_ID, FROM, to))
                .doesNotThrowAnyException();
    }

    @Test
    void 상한을_1초라도_넘으면_거부한다() {
        Instant to = FROM.plus(Duration.ofDays(62)).plusSeconds(1);

        assertThatThrownBy(() -> service.getEvents(STREAMER_ID, FROM, to))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_RANGE_TOO_WIDE");

        verify(repository, never()).findRange(anyLong(), any(), any());
    }

    @Test
    void 시작과_끝이_같으면_거부한다() {
        assertThatThrownBy(() -> service.getEvents(STREAMER_ID, FROM, FROM))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_RANGE_INVALID");
    }

    @Test
    void 끝이_시작보다_빠르면_거부한다() {
        assertThatThrownBy(() -> service.getEvents(STREAMER_ID, FROM, FROM.minusSeconds(1)))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_RANGE_INVALID");
    }

    @Test
    void 정상_범위는_Repository_결과를_그대로_돌려준다() {
        Instant to = FROM.plus(Duration.ofDays(30));
        CalendarEvent event = new CalendarEvent(
                44L, STREAMER_ID, null, CalendarEventType.DAY_OFF, "휴방", null,
                FROM, FROM.plusSeconds(3600), CalendarEventStatus.SCHEDULED
        );
        when(repository.findRange(STREAMER_ID, FROM, to)).thenReturn(List.of(event));

        assertThat(service.getEvents(STREAMER_ID, FROM, to)).containsExactly(event);
    }

    @Test
    void 없는_일정_상세는_404다() {
        when(repository.findDetail(STREAMER_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEvent(STREAMER_ID, 999L))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_NOT_FOUND");
    }
}

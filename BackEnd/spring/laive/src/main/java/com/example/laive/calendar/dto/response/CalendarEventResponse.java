package com.example.laive.calendar.dto.response;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;

import java.time.Instant;

/**
 * 일정 한 건의 응답이다.
 *
 * <p>promiseId는 이 일정이 어느 약속에서 전환됐는지를 가리킨다. null이면 사용자가 직접 만든
 * 일정이다. 프론트는 이 값으로 "약속에서 만든 일정" 표시와 약속 역참조를 한다.
 */
public record CalendarEventResponse(
        long calendarEventId,
        Long platformAccountId,
        CalendarEventType eventType,
        String title,
        String memo,
        Instant startAt,
        Instant endAt,
        CalendarEventStatus status,
        Long promiseId
) {
    public static CalendarEventResponse from(CalendarEvent event) {
        return new CalendarEventResponse(
                event.calendarEventId(),
                event.platformAccountId(),
                event.eventType(),
                event.title(),
                event.memo(),
                event.startAt(),
                event.endAt(),
                event.status(),
                event.promiseId()
        );
    }
}

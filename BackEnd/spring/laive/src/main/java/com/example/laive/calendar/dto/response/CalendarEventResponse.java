package com.example.laive.calendar.dto.response;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;

import java.time.Instant;

public record CalendarEventResponse(
        long calendarEventId,
        Long platformAccountId,
        CalendarEventType eventType,
        String title,
        String memo,
        Instant startAt,
        Instant endAt,
        CalendarEventStatus status
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
                event.status()
        );
    }
}

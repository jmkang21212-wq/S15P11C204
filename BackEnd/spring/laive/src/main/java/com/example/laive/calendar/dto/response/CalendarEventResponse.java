package com.example.laive.calendar.dto.response;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;

import java.time.Instant;
import java.util.List;

public record CalendarEventResponse(
        long calendarEventId,
        Long platformAccountId,
        List<Long> platformAccountIds,
        CalendarEventType eventType,
        String title,
        String memo,
        Instant startAt,
        Instant endAt,
        CalendarEventStatus status
) {
    public static CalendarEventResponse from(CalendarEvent event) {
        return from(event, event.platformAccountId() == null
                ? List.of()
                : List.of(event.platformAccountId()));
    }

    public static CalendarEventResponse from(CalendarEvent event, List<Long> platformAccountIds) {
        List<Long> selected = platformAccountIds.isEmpty() && event.platformAccountId() != null
                ? List.of(event.platformAccountId())
                : List.copyOf(platformAccountIds);
        return new CalendarEventResponse(
                event.calendarEventId(),
                event.platformAccountId(),
                selected,
                event.eventType(),
                event.title(),
                event.memo(),
                event.startAt(),
                event.endAt(),
                event.status()
        );
    }
}

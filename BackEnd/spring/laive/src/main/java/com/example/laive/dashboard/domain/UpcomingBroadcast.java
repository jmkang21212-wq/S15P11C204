package com.example.laive.dashboard.domain;

import com.example.laive.calendar.domain.CalendarEventStatus;

import java.time.Instant;

public record UpcomingBroadcast(
        long calendarEventId,
        String title,
        Instant startAt,
        Instant endAt,
        CalendarEventStatus status
) {
}

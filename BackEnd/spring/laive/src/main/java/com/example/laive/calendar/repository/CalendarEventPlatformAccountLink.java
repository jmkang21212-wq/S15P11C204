package com.example.laive.calendar.repository;

/** Read model for a calendar event's ordered platform-account assignment. */
public record CalendarEventPlatformAccountLink(
        long calendarEventId,
        long platformAccountId
) {
}

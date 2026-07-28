package com.example.laive.broadcast.session.repository;

/**
 * 방송 시작 검증에 필요한 일정 최소 정보다.
 */
public record CalendarEventReference(
        long calendarEventId,
        long streamerId,
        Long platformAccountId,
        String eventType,
        String status
) {
}


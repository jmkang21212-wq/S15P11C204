package com.example.laive.calendar.domain;

import java.time.Instant;

/**
 * Controller DTO에서 검증된 일정 생성 입력을 Service로 전달한다.
 */
public record CreateCalendarEventCommand(
        Long platformAccountId,
        CalendarEventType eventType,
        String title,
        String memo,
        Instant startAt,
        Instant endAt
) {
}

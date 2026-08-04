package com.example.laive.calendar.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Controller DTO에서 검증된 일정 생성 입력을 Service로 전달한다.
 */
public record CreateCalendarEventCommand(
        Long platformAccountId,
        List<Long> platformAccountIds,
        CalendarEventType eventType,
        String title,
        String memo,
        Instant startAt,
        Instant endAt
) {
    public CreateCalendarEventCommand {
        platformAccountIds = platformAccountIds == null
                ? (platformAccountId == null ? List.of() : List.of(platformAccountId))
                : List.copyOf(new LinkedHashSet<>(platformAccountIds));
    }

    public CreateCalendarEventCommand(
            Long platformAccountId,
            CalendarEventType eventType,
            String title,
            String memo,
            Instant startAt,
            Instant endAt
    ) {
        this(platformAccountId, null, eventType, title, memo, startAt, endAt);
    }
}

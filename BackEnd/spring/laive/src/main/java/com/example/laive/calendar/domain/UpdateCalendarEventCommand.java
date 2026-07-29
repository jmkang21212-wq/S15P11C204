package com.example.laive.calendar.domain;

import com.example.laive.common.api.PatchValue;

import java.time.Instant;

/**
 * 일정 부분 수정 입력이다. 각 필드는 "미전송(변경 없음)"과 "명시적 null(비우기)"을 구분해야 하므로
 * 값을 PatchValue로 감싼다.
 */
public record UpdateCalendarEventCommand(
        PatchValue<Long> platformAccountId,
        PatchValue<CalendarEventType> eventType,
        PatchValue<String> title,
        PatchValue<String> memo,
        PatchValue<Instant> startAt,
        PatchValue<Instant> endAt,
        PatchValue<CalendarEventStatus> status
) {
}

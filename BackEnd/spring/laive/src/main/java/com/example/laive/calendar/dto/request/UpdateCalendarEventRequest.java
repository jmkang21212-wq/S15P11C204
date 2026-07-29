package com.example.laive.calendar.dto.request;

import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.common.api.PatchValue;

import java.time.Instant;

/**
 * 일정 부분 수정 API 입력이다 (API 8.3).
 *
 * 값 범위 검증을 jakarta 애노테이션으로 걸 수 없어 도메인 생성자에 맡긴다. PatchValue로 감싸면
 * 검증 대상이 래퍼가 되어 애노테이션이 안쪽 값에 닿지 않기 때문이다.
 */
public record UpdateCalendarEventRequest(
        PatchValue<Long> platformAccountId,
        PatchValue<CalendarEventType> eventType,
        PatchValue<String> title,
        PatchValue<String> memo,
        PatchValue<Instant> startAt,
        PatchValue<Instant> endAt,
        PatchValue<CalendarEventStatus> status
) {
    public UpdateCalendarEventCommand toCommand() {
        return new UpdateCalendarEventCommand(
                platformAccountId,
                eventType,
                title,
                memo,
                startAt,
                endAt,
                status
        );
    }
}

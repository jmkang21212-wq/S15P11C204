package com.example.laive.calendar.dto.request;

import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.common.api.PatchValue;
import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 일정 부분 수정 API 입력이다 (API 8.3).
 *
 * 값 범위 검증을 jakarta 애노테이션으로 걸 수 없어 도메인 생성자에 맡긴다. PatchValue로 감싸면
 * 검증 대상이 래퍼가 되어 애노테이션이 안쪽 값에 닿지 않기 때문이다.
 */
public record UpdateCalendarEventRequest(
        PatchValue<Long> platformAccountId,
        PatchValue<List<Long>> platformAccountIds,
        PatchValue<CalendarEventType> eventType,
        PatchValue<String> title,
        PatchValue<String> memo,
        PatchValue<Instant> startAt,
        PatchValue<Instant> endAt,
        PatchValue<CalendarEventStatus> status
) {
    public UpdateCalendarEventRequest(
            PatchValue<Long> platformAccountId,
            PatchValue<CalendarEventType> eventType,
            PatchValue<String> title,
            PatchValue<String> memo,
            PatchValue<Instant> startAt,
            PatchValue<Instant> endAt,
            PatchValue<CalendarEventStatus> status
    ) {
        this(platformAccountId, null, eventType, title, memo, startAt, endAt, status);
    }

    @AssertTrue(message = "platformAccountId and platformAccountIds cannot be patched together")
    public boolean isPlatformSelectionExclusive() {
        return platformAccountId == null || platformAccountIds == null;
    }

    @AssertTrue(message = "platformAccountIds must contain distinct positive IDs")
    public boolean isPlatformSelectionValid() {
        if (platformAccountIds == null || platformAccountIds.value() == null) {
            return true;
        }
        List<Long> values = platformAccountIds.value();
        return values.size() <= 10
                && values.stream().allMatch(id -> id != null && id > 0)
                && new LinkedHashSet<>(values).size() == values.size();
    }

    public UpdateCalendarEventCommand toCommand() {
        PatchValue<Long> primaryPatch = platformAccountIds == null
                ? platformAccountId
                : PatchValue.of(platformAccountIds.value() == null || platformAccountIds.value().isEmpty()
                    ? null
                    : platformAccountIds.value().getFirst());
        return new UpdateCalendarEventCommand(
                primaryPatch,
                platformAccountIds,
                eventType,
                title,
                memo,
                startAt,
                endAt,
                status
        );
    }
}

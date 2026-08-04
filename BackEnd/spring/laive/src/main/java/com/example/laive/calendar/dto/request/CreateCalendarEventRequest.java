package com.example.laive.calendar.dto.request;

import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 일정 생성 API 입력이다. 상태는 항상 SCHEDULED로 시작하므로 받지 않는다.
 */
public record CreateCalendarEventRequest(
        @Positive(message = "양수여야 합니다.")
        Long platformAccountId,

        @Size(min = 1, max = 10)
        List<@Positive Long> platformAccountIds,

        @NotNull(message = "필수 값입니다.")
        CalendarEventType eventType,

        @NotBlank(message = "비어 있을 수 없습니다.")
        @Size(max = 200, message = "200자 이하여야 합니다.")
        String title,

        @Size(max = 2000, message = "2000자 이하여야 합니다.")
        String memo,

        @NotNull(message = "필수 값입니다.")
        Instant startAt,

        @NotNull(message = "필수 값입니다.")
        Instant endAt
) {
    @AssertTrue(message = "platformAccountId and platformAccountIds cannot be used together")
    public boolean isPlatformSelectionExclusive() {
        return platformAccountId == null || platformAccountIds == null || platformAccountIds.isEmpty();
    }

    @AssertTrue(message = "platformAccountIds must not contain duplicates")
    public boolean isPlatformSelectionDistinct() {
        return platformAccountIds == null
                || new LinkedHashSet<>(platformAccountIds).size() == platformAccountIds.size();
    }

    public CreateCalendarEventCommand toCommand() {
        List<Long> selected = platformAccountId != null
                ? List.of(platformAccountId)
                : platformAccountIds == null ? List.of() : List.copyOf(platformAccountIds);
        return new CreateCalendarEventCommand(
                selected.isEmpty() ? null : selected.getFirst(),
                selected,
                eventType,
                title,
                memo,
                startAt,
                endAt
        );
    }
}

package com.example.laive.calendar.dto.request;

import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 일정 생성 API 입력이다. 상태는 항상 SCHEDULED로 시작하므로 받지 않는다.
 */
public record CreateCalendarEventRequest(
        @Positive(message = "양수여야 합니다.")
        Long platformAccountId,

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
    public CreateCalendarEventCommand toCommand() {
        return new CreateCalendarEventCommand(
                platformAccountId,
                eventType,
                title,
                memo,
                startAt,
                endAt
        );
    }
}

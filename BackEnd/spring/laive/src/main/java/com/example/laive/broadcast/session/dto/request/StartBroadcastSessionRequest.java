package com.example.laive.broadcast.session.dto.request;

import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 방송 분석 시작 API 입력이다. 사용자나 클라이언트 시각은 받지 않는다.
 */
public record StartBroadcastSessionRequest(
        @NotNull(message = "필수 값입니다.")
        @Positive(message = "양수여야 합니다.")
        Long platformAccountId,

        @Positive(message = "양수여야 합니다.")
        Long calendarEventId,

        @NotBlank(message = "비어 있을 수 없습니다.")
        @Size(max = 200, message = "200자 이하여야 합니다.")
        String title,

        @Size(max = 50, message = "50자 이하여야 합니다.")
        String category,

        @Size(max = 10, message = "10자 이하여야 합니다.")
        @Pattern(
                regexp = "^[a-z]{2}(-[A-Z]{2})?$",
                message = "ko, en, ko-KR 형식이어야 합니다."
        )
        String language
) {
    public StartBroadcastSessionCommand toCommand() {
        return new StartBroadcastSessionCommand(
                platformAccountId,
                calendarEventId,
                title,
                category,
                language
        );
    }
}


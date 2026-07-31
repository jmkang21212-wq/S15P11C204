package com.example.laive.broadcast.analysis.dto.request;

import com.example.laive.broadcast.analysis.domain.PromiseDetection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * AI 서버가 감지한 약속 후보다(명세 13.5).
 *
 * <p>deadlineAt은 AI가 "2주 안으로" 같은 표현을 이미 절대 시각으로 해석한 값이다.
 * 기한 미정이거나 조건부 약속이면 null이다.
 */
public record PromiseDetectionRequest(
        @NotBlank(message = "비어 있을 수 없습니다.")
        @Size(max = 500, message = "500자 이하여야 합니다.")
        String content,

        @NotBlank(message = "비어 있을 수 없습니다.")
        String rawText,

        Instant deadlineAt,

        @NotNull(message = "필수 값입니다.")
        Instant spokenAt
) {
    public PromiseDetection toDomain() {
        return new PromiseDetection(content, rawText, deadlineAt, spokenAt);
    }
}

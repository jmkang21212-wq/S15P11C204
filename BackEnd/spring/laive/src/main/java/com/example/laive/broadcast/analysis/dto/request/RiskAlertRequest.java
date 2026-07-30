package com.example.laive.broadcast.analysis.dto.request;

import com.example.laive.broadcast.analysis.domain.RiskAlert;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * AI 서버가 감지 즉시 보내는 위험 발언이다(명세 13.5).
 *
 * <p>severity와 category는 RISK_EVENTS의 CHECK 제약과 같은 값만 받는다. 여기서
 * 막지 않으면 DB 제약 위반이 500으로 새어 나간다.
 */
public record RiskAlertRequest(
        @NotBlank(message = "비어 있을 수 없습니다.")
        @Pattern(regexp = "DICTIONARY|LLM", message = "DICTIONARY 또는 LLM이어야 합니다.")
        String source,

        @NotBlank(message = "비어 있을 수 없습니다.")
        @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "HIGH, MEDIUM, LOW 중 하나여야 합니다.")
        String severity,

        @NotBlank(message = "비어 있을 수 없습니다.")
        @Pattern(
                regexp = "PROFANITY|POLITICS|DISCRIMINATION|SEXUAL|DEFAMATION|OTHER",
                message = "허용된 위험 유형이 아닙니다."
        )
        String category,

        @NotBlank(message = "비어 있을 수 없습니다.")
        String text,

        @NotNull(message = "필수 값입니다.")
        Instant startedAt,

        @NotNull(message = "필수 값입니다.")
        Instant endedAt,

        String reason
) {
    public RiskAlert toDomain() {
        return new RiskAlert(source, severity, category, text, startedAt, endedAt, reason);
    }
}

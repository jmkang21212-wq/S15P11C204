package com.example.laive.broadcast.analysis.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * AI 서버가 감지한 약속 후보 한 건이다(명세 13.5).
 *
 * <p>기한은 AI가 "2주 안으로" 같은 표현을 절대 시각으로 이미 해석해서 보낸다.
 * Spring은 한국어 기한 표현을 파싱하지 않는다.
 */
public record PromiseDetection(
        String content,
        String rawText,
        Instant deadlineAt,
        Instant spokenAt
) {
    /** PROMISES.due_date는 DATE다. 기한 미정 약속은 null을 허용한다(명세 1.5). */
    public LocalDate dueDate() {
        return deadlineAt == null ? null : deadlineAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    public String briefingMessage() {
        return "약속이 감지되었습니다: " + content;
    }
}

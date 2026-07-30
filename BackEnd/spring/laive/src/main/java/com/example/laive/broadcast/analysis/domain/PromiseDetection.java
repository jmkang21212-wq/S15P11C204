package com.example.laive.broadcast.analysis.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
    /**
     * 절대 시각을 날짜로 자를 때 쓰는 기준 시간대다.
     *
     * <p>UTC로 자르면 스트리머가 기대한 날짜보다 하루 빨라진다. deadlineAt이 15:00~23:59Z면
     * 한국 시각으로는 이미 다음 날이기 때문이다. 자정을 넘겨 방송하는 경우가 흔해 실제로 밟는다.
     * DB 컨테이너도 TZ=Asia/Seoul로 맞춰져 있다.
     */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** PROMISES.due_date는 DATE다. 기한 미정 약속은 null을 허용한다(명세 1.5). */
    public LocalDate dueDate() {
        return deadlineAt == null ? null : deadlineAt.atZone(SERVICE_ZONE).toLocalDate();
    }

    public String briefingMessage() {
        return "약속이 감지되었습니다: " + content;
    }
}

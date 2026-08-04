package com.example.laive.broadcast.analysis.domain;

import java.time.Instant;

/**
 * AI 서버가 감지한 위험 발언 한 건이다.
 *
 * <p>{@code text}가 함께 오는 것이 중요하다. 위험 경고는 즉시 전송되고 전사는 배치라
 * 경고가 자기 전사보다 먼저 도착한다. 경고에 원문이 들어 있어야 Spring이 전사를
 * 만들어 붙일 수 있고, 두 스트림의 도착 순서를 따지지 않아도 된다.
 */
public record RiskAlert(
        String source,
        String severity,
        String category,
        String text,
        Instant startedAt,
        Instant endedAt,
        String reason
) {
    /** 실시간 콘솔에 띄울 브리핑 문구다(명세 13.5의 briefingMessage). */
    public String briefingMessage() {
        if (reason != null && !reason.isBlank()) {
            return reason.trim();
        }
        return "위험 소지가 있는 표현이 감지되었습니다.";
    }
}

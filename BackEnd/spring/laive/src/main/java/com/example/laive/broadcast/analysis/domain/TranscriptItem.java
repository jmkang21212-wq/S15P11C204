package com.example.laive.broadcast.analysis.domain;

import java.time.Instant;

/**
 * AI 서버가 보낸 STT 발화 한 건이다.
 *
 * <p>AI는 벽시계 시각으로 보내고 DB는 방송 시작 기준 밀리초로 저장한다(명세 4.2).
 * 변환에 필요한 방송 시작 시각은 AI가 알 수 없으므로 Spring이 세션에서 읽어 환산한다.
 */
public record TranscriptItem(
        String text,
        Instant startedAt,
        Instant endedAt
) {
}

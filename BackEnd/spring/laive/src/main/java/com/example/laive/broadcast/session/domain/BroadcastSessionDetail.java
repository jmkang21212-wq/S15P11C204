package com.example.laive.broadcast.session.domain;

import java.time.Instant;

/**
 * 상세 및 현재 방송 조회에 사용하는 읽기 전용 Projection이다.
 */
public record BroadcastSessionDetail(
        long sessionId,
        long platformAccountId,
        Long calendarEventId,
        String title,
        String category,
        String language,
        String thumbnailUrl,
        String vodUrl,
        BroadcastStatus status,
        Instant startedAt,
        Instant endedAt
) {
}


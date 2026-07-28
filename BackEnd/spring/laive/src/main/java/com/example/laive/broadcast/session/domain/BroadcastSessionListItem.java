package com.example.laive.broadcast.session.domain;

import java.time.Instant;

/**
 * 방송 이력 목록 전용 Projection이다.
 */
public record BroadcastSessionListItem(
        long sessionId,
        String title,
        String category,
        String thumbnailUrl,
        BroadcastStatus status,
        Instant startedAt,
        Instant endedAt,
        String reportStatus
) {
}


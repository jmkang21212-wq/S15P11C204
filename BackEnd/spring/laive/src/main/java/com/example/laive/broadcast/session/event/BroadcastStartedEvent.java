package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 필수 초기화가 끝나 세션이 LIVE로 전환됐음을 알린다.
 */
public record BroadcastStartedEvent(
        UUID eventId,
        short schemaVersion,
        long sessionId,
        long streamerId,
        Instant startedAt,
        Instant occurredAt
) {
}


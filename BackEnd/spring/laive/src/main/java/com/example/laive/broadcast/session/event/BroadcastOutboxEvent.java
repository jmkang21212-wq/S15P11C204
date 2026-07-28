package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispatcher가 처리할 Outbox 이벤트다. payload는 버전이 지정된 JSON 문자열이다.
 */
public record BroadcastOutboxEvent(
        UUID eventId,
        long aggregateId,
        BroadcastOutboxEventType eventType,
        short schemaVersion,
        String deduplicationKey,
        String payload,
        int attempts,
        Instant createdAt
) {
}


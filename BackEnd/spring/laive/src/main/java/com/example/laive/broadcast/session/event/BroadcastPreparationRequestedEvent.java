package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.UUID;

/**
 * PREPARING 세션의 Redis·채팅·STT·AI 초기화를 요청한다.
 */
public record BroadcastPreparationRequestedEvent(
        UUID eventId,
        short schemaVersion,
        long sessionId,
        long streamerId,
        long platformAccountId,
        Instant startedAt,
        String language,
        Instant occurredAt
) {
}


package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.UUID;

/** 플랫폼 자식 하나의 live 탐지와 수집기 시작을 요청한다. */
public record PlatformSessionPreparationRequestedEvent(
        UUID eventId,
        short schemaVersion,
        long sessionId,
        long platformSessionId,
        long streamerId,
        long platformAccountId,
        String platformCode,
        Instant requestedAt,
        String language,
        Instant occurredAt
) {
}

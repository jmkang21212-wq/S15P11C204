package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 정상 종료된 세션의 수집기 정리와 리포트 생성을 요청한다.
 */
public record BroadcastEndedEvent(
        UUID eventId,
        short schemaVersion,
        long sessionId,
        long streamerId,
        long platformAccountId,
        long reportId,
        Instant startedAt,
        Instant endedAt,
        Instant occurredAt
) {
}


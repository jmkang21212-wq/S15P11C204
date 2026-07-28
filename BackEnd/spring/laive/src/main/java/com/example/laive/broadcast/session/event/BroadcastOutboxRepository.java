package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 저장·선점·재시도 상태 변경을 추상화한다.
 */
public interface BroadcastOutboxRepository {

    boolean save(
            UUID eventId,
            long aggregateId,
            BroadcastOutboxEventType eventType,
            String deduplicationKey,
            String payload,
            Instant availableAt
    );

    List<BroadcastOutboxEvent> claim(
            String workerId,
            Instant now,
            Instant lockedUntil,
            int limit
    );

    void markPublished(UUID eventId, Instant publishedAt);

    void scheduleRetry(
            UUID eventId,
            int attempts,
            Instant availableAt,
            String lastError
    );

    void markDead(UUID eventId, int attempts, String lastError);
}


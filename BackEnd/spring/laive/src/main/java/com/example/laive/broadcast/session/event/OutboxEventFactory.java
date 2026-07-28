package com.example.laive.broadcast.session.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 이벤트 식별자, 버전, 중복 방지 Key와 JSON 직렬화를 한곳에서 생성한다.
 */
@Component
public class OutboxEventFactory {

    public static final short SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PendingOutboxEvent preparationRequested(
            long sessionId,
            long streamerId,
            long platformAccountId,
            Instant startedAt,
            String language,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                sessionId,
                BroadcastOutboxEventType.PREPARATION_REQUESTED,
                "broadcast-session:" + sessionId + ":prepare",
                new BroadcastPreparationRequestedEvent(
                        eventId,
                        SCHEMA_VERSION,
                        sessionId,
                        streamerId,
                        platformAccountId,
                        startedAt,
                        language,
                        occurredAt
                ),
                occurredAt
        );
    }

    public PendingOutboxEvent started(
            long sessionId,
            long streamerId,
            Instant startedAt,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                sessionId,
                BroadcastOutboxEventType.BROADCAST_STARTED,
                "broadcast-session:" + sessionId + ":started",
                new BroadcastStartedEvent(
                        eventId,
                        SCHEMA_VERSION,
                        sessionId,
                        streamerId,
                        startedAt,
                        occurredAt
                ),
                occurredAt
        );
    }

    public PendingOutboxEvent ended(
            long sessionId,
            long streamerId,
            long platformAccountId,
            long reportId,
            Instant startedAt,
            Instant endedAt,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                sessionId,
                BroadcastOutboxEventType.BROADCAST_ENDED,
                "broadcast-session:" + sessionId + ":ended",
                new BroadcastEndedEvent(
                        eventId,
                        SCHEMA_VERSION,
                        sessionId,
                        streamerId,
                        platformAccountId,
                        reportId,
                        startedAt,
                        endedAt,
                        occurredAt
                ),
                occurredAt
        );
    }

    private PendingOutboxEvent pending(
            UUID eventId,
            long aggregateId,
            BroadcastOutboxEventType eventType,
            String deduplicationKey,
            Object payload,
            Instant availableAt
    ) {
        try {
            return new PendingOutboxEvent(
                    eventId,
                    aggregateId,
                    eventType,
                    deduplicationKey,
                    objectMapper.writeValueAsString(payload),
                    availableAt
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("방송 세션 이벤트 직렬화에 실패했습니다.", exception);
        }
    }

    public record PendingOutboxEvent(
            UUID eventId,
            long aggregateId,
            BroadcastOutboxEventType eventType,
            String deduplicationKey,
            String payload,
            Instant availableAt
    ) {
    }
}


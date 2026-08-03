package com.example.laive.broadcast.session.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import com.example.laive.report.event.ReportFinalizationRequestedEvent;
import com.example.laive.report.event.TriggerAnalysisRequestedEvent;
import com.example.laive.growth.event.GrowthActionGenerationRequestedEvent;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationTrigger;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.event.GrowthRoadmapGenerationRequestedEvent;

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

    public PendingOutboxEvent platformSessionPreparationRequested(
            long sessionId,
            long platformSessionId,
            long streamerId,
            long platformAccountId,
            String platformCode,
            Instant requestedAt,
            String language,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                sessionId,
                BroadcastOutboxEventType.PLATFORM_SESSION_PREPARATION_REQUESTED,
                "platform-session:" + platformSessionId + ":prepare",
                new PlatformSessionPreparationRequestedEvent(
                        eventId,
                        SCHEMA_VERSION,
                        sessionId,
                        platformSessionId,
                        streamerId,
                        platformAccountId,
                        platformCode,
                        requestedAt,
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

    public PendingOutboxEvent triggerAnalysisRequested(
            long sessionId,
            long reportId,
            long triggerId,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                sessionId,
                BroadcastOutboxEventType.TRIGGER_ANALYSIS_REQUESTED,
                "report:" + reportId + ":trigger:" + triggerId,
                new TriggerAnalysisRequestedEvent(
                        eventId, SCHEMA_VERSION, sessionId, reportId, triggerId, occurredAt
                ),
                occurredAt
        );
    }

    public PendingOutboxEvent reportFinalizationRequested(
            long sessionId,
            long reportId,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                sessionId,
                BroadcastOutboxEventType.REPORT_FINALIZATION_REQUESTED,
                "report:" + reportId + ":finalize",
                new ReportFinalizationRequestedEvent(
                        eventId, SCHEMA_VERSION, sessionId, reportId, occurredAt
                ),
                occurredAt
        );
    }

    public PendingOutboxEvent growthActionGenerationRequested(
            long generationId,
            long goalId,
            long goalRevision,
            Long sourceReportId,
            String promptVersion,
            String deduplicationKey,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                goalId,
                BroadcastOutboxEventType.GROWTH_ACTION_GENERATION_REQUESTED,
                deduplicationKey,
                new GrowthActionGenerationRequestedEvent(
                        eventId, SCHEMA_VERSION, generationId, goalId, goalRevision,
                        sourceReportId, promptVersion, occurredAt
                ),
                occurredAt
        );
    }

    public PendingOutboxEvent growthRoadmapGenerationRequested(
            long generationId,
            long roadmapId,
            long streamerId,
            Long sourceReportId,
            RoadmapGenerationTrigger triggerType,
            RoadmapQuality quality,
            String promptVersion,
            String deduplicationKey,
            Instant occurredAt,
            Instant availableAt
    ) {
        UUID eventId = UUID.randomUUID();
        return pending(
                eventId,
                roadmapId,
                BroadcastOutboxEventType.GROWTH_ROADMAP_GENERATION_REQUESTED,
                deduplicationKey,
                new GrowthRoadmapGenerationRequestedEvent(
                        eventId, SCHEMA_VERSION, generationId, roadmapId, streamerId,
                        sourceReportId, triggerType, quality, promptVersion, occurredAt
                ),
                availableAt
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

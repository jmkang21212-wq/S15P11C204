package com.example.laive.broadcast.session.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.PlatformSessionPreparationRequestedEvent;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.example.laive.broadcast.session.repository.mybatis.BroadcastPlatformSessionMapper;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.platform.core.ExternalBroadcast;
import com.example.laive.platform.core.PlatformAdapterRegistry;
import com.example.laive.platform.core.PlatformIntegrationAdapter;
import com.example.laive.platform.core.PlatformCapabilityCode;
import com.example.laive.platform.service.PlatformAccountService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Prepares one provider child session without coupling the parent lifecycle to a provider. */
@Component
public class PlatformSessionPreparationRequestedHandler implements BroadcastOutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final PlatformAdapterRegistry adapterRegistry;
    private final PlatformAccountService platformAccountService;
    private final BroadcastPlatformSessionMapper platformSessionMapper;
    private final SessionRedisGateway redisGateway;
    private final ChatCollectorGateway collectorGateway;
    private final AiSessionGateway aiSessionGateway;
    private final BroadcastSessionTransactionService transactionService;

    public PlatformSessionPreparationRequestedHandler(
            ObjectMapper objectMapper,
            PlatformAdapterRegistry adapterRegistry,
            PlatformAccountService platformAccountService,
            BroadcastPlatformSessionMapper platformSessionMapper,
            SessionRedisGateway redisGateway,
            ChatCollectorGateway collectorGateway,
            AiSessionGateway aiSessionGateway,
            BroadcastSessionTransactionService transactionService
    ) {
        this.objectMapper = objectMapper;
        this.adapterRegistry = adapterRegistry;
        this.platformAccountService = platformAccountService;
        this.platformSessionMapper = platformSessionMapper;
        this.redisGateway = redisGateway;
        this.collectorGateway = collectorGateway;
        this.aiSessionGateway = aiSessionGateway;
        this.transactionService = transactionService;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.PLATFORM_SESSION_PREPARATION_REQUESTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent outboxEvent) {
        PlatformSessionPreparationRequestedEvent event = read(outboxEvent);
        PlatformIntegrationAdapter adapter = adapterRegistry.require(event.platformCode());

        Optional<ExternalBroadcast> activeBroadcast = adapter.liveDiscovery().map(discovery -> {
            String accessToken = platformAccountService.accessToken(
                    event.streamerId(), event.platformAccountId());
            ExternalBroadcast broadcast = discovery.findActiveBroadcast(accessToken)
                    .orElseThrow(() -> new IllegalStateException(
                            "active broadcast not found: platform=" + event.platformCode()));
            saveResources(event.platformSessionId(), broadcast);
            return broadcast;
        });

        for (String capability : adapter.descriptor().capabilities()) {
            if (PlatformCapabilityCode.SESSION_CAPABILITIES.contains(capability)) {
                platformSessionMapper.markCapabilityRunning(
                        event.platformSessionId(), capability);
            }
        }

        // These operations are idempotent and are repeated on an Outbox retry.
        redisGateway.initialize(event.sessionId(), event.streamerId(), event.requestedAt());
        aiSessionGateway.start(event.sessionId(), event.language());
        collectorGateway.startPlatformSession(
                event.sessionId(),
                event.platformSessionId(),
                event.streamerId(),
                event.platformAccountId(),
                adapter.platformCode().value()
        );
        Instant platformStartedAt = activeBroadcast
                .map(ExternalBroadcast::startedAt)
                .orElse(event.requestedAt());
        transactionService.activatePlatform(event.platformSessionId(), platformStartedAt);
    }

    @Override
    public void onDead(BroadcastOutboxEvent event) {
        try {
            PlatformSessionPreparationRequestedEvent payload = read(event);
            transactionService.failPlatformInitialization(
                    payload.platformSessionId(), "PREPARATION_RETRY_EXHAUSTED");
        } catch (RuntimeException ignored) {
            // A malformed payload cannot safely identify the child session.
        }
    }

    private void saveResources(long platformSessionId, ExternalBroadcast broadcast) {
        platformSessionMapper.upsertResource(
                platformSessionId,
                "BROADCAST",
                broadcast.externalBroadcastId(),
                broadcast.watchUrl(),
                json(broadcast.metadata())
        );
        if (broadcast.liveChatId() != null && !broadcast.liveChatId().isBlank()) {
            platformSessionMapper.upsertResource(
                    platformSessionId,
                    "LIVE_CHAT",
                    broadcast.liveChatId(),
                    null,
                    "{}"
            );
        }
    }

    private PlatformSessionPreparationRequestedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(
                    event.payload(), PlatformSessionPreparationRequestedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read platform preparation event payload", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize platform resource metadata", exception);
        }
    }
}

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
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import com.example.laive.platform.service.PlatformAccountService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Prepares one provider child session without coupling the parent lifecycle to a provider. */
@Component
public class PlatformSessionPreparationRequestedHandler implements BroadcastOutboxEventHandler {

    private static final Logger log =
            LoggerFactory.getLogger(PlatformSessionPreparationRequestedHandler.class);

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

        launchBroadcast(event, adapter);

        Optional<ExternalBroadcast> activeBroadcast = adapter.liveDiscovery().map(discovery -> {
            String accessToken = platformAccountService.accessToken(
                    event.streamerId(), event.platformAccountId());
            ExternalBroadcast broadcast = discovery.findActiveBroadcast(accessToken)
                    .orElseThrow(() -> new IllegalStateException(
                            "active broadcast not found: platform=" + event.platformCode()));
            saveResources(event.platformSessionId(), broadcast);
            return broadcast;
        });

        syncBroadcastTitle(event, adapter);

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

    /**
     * 플랫폼에 방송을 켜 둔다. discovery <b>앞에</b> 있어야 한다 — 찾을 대상을 먼저
     * 만들어야 하기 때문이다.
     *
     * <p>제목 동기화와 달리 실패를 삼키지 않는다. 방송을 못 만들면 discovery도 반드시
     * 실패하므로, 여기서 예외를 올려 Outbox 재시도에 맡기는 편이 낫다. 첫 시도에서
     * 방송을 만들고 나면 이후 재시도는 "이미 있음"으로 빠르게 지나간다.
     *
     * <p>이 Capability가 없는 플랫폼(치지직)은 아무 일도 일어나지 않는다.
     */
    private void launchBroadcast(
            PlatformSessionPreparationRequestedEvent event,
            PlatformIntegrationAdapter adapter
    ) {
        adapter.broadcastLaunch().ifPresent(launch -> {
            String accessToken = platformAccountService.accessToken(
                    event.streamerId(), event.platformAccountId());
            // title이 null이면 필드 추가 이전에 저장된 페이로드다. 제목 없이도 방송은 켠다.
            launch.ensureLaunched(accessToken, event.title() == null ? "LAIVE" : event.title());
        });
    }

    /**
     * 세션 제목을 플랫폼 방송 제목에도 반영한다. 부가 기능이라 실패해도 방송 시작을
     * 막지 않는다 — 권한 부족·토큰 거부·연결 실패 전부 warn만 남긴다.
     * Outbox 재시도에서 같은 제목으로 다시 호출돼도 멱등하다.
     */
    private void syncBroadcastTitle(
            PlatformSessionPreparationRequestedEvent event,
            PlatformIntegrationAdapter adapter
    ) {
        // title이 null이면 필드 추가 이전에 저장된 페이로드다.
        if (event.title() == null) {
            return;
        }
        adapter.liveSetting().ifPresent(liveSetting -> {
            try {
                String accessToken = platformAccountService.accessToken(
                        event.streamerId(), event.platformAccountId());
                liveSetting.updateLiveSetting(accessToken, new PlatformLiveSettingUpdate(event.title()));
            } catch (RuntimeException exception) {
                log.warn("방송 제목 동기화 실패: platformSessionId={} cause={}",
                        event.platformSessionId(), exception.getClass().getSimpleName());
            }
        });
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

package com.example.laive.broadcast.session.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.PlatformSessionPreparationRequestedEvent;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.example.laive.broadcast.session.repository.mybatis.BroadcastPlatformSessionMapper;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.platform.core.PlatformAdapterRegistry;
import com.example.laive.platform.core.PlatformCode;
import com.example.laive.platform.core.PlatformDescriptor;
import com.example.laive.platform.core.PlatformIntegrationAdapter;
import com.example.laive.platform.core.PlatformLiveSettingCapability;
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 세션 준비 이벤트 처리 중 플랫폼 방송 제목 동기화가 best-effort로 동작하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformSessionPreparationRequestedHandlerTest {

    private static final long SESSION_ID = 100L;
    private static final long PLATFORM_SESSION_ID = 200L;
    private static final long STREAMER_ID = 7L;
    private static final long PLATFORM_ACCOUNT_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");

    private final ObjectMapper objectMapper =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Mock
    private PlatformAdapterRegistry adapterRegistry;

    @Mock
    private com.example.laive.platform.service.PlatformAccountService platformAccountService;

    @Mock
    private BroadcastPlatformSessionMapper platformSessionMapper;

    @Mock
    private SessionRedisGateway redisGateway;

    @Mock
    private ChatCollectorGateway collectorGateway;

    @Mock
    private AiSessionGateway aiSessionGateway;

    @Mock
    private BroadcastSessionTransactionService transactionService;

    @Mock
    private PlatformIntegrationAdapter adapter;

    @Mock
    private PlatformLiveSettingCapability liveSettingCapability;

    private PlatformSessionPreparationRequestedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlatformSessionPreparationRequestedHandler(
                objectMapper,
                adapterRegistry,
                platformAccountService,
                platformSessionMapper,
                redisGateway,
                collectorGateway,
                aiSessionGateway,
                transactionService
        );
        when(adapterRegistry.require("CHZZK")).thenReturn(adapter);
        when(adapter.liveDiscovery()).thenReturn(Optional.empty());
        when(adapter.liveSetting()).thenReturn(Optional.of(liveSettingCapability));
        when(adapter.platformCode()).thenReturn(PlatformCode.of("CHZZK"));
        when(adapter.descriptor()).thenReturn(new PlatformDescriptor(
                PlatformCode.of("CHZZK"), "치지직", true, Set.of(), null));
        when(platformAccountService.accessToken(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .thenReturn("access-token-value");
    }

    @Test
    void syncsSessionTitleToPlatform() {
        handler.handle(outboxEvent("오늘 방송 제목"));

        verify(liveSettingCapability).updateLiveSetting(
                "access-token-value", new PlatformLiveSettingUpdate("오늘 방송 제목"));
        verify(transactionService).activatePlatform(PLATFORM_SESSION_ID, NOW);
    }

    @Test
    void completesPreparationEvenWhenTitleSyncFails() {
        // 제목 동기화는 부가 기능이라 실패해도 방송 시작(활성화)을 막지 않는다.
        doThrow(new RuntimeException("chzzk down"))
                .when(liveSettingCapability).updateLiveSetting(anyString(), any());

        handler.handle(outboxEvent("오늘 방송 제목"));

        verify(transactionService).activatePlatform(PLATFORM_SESSION_ID, NOW);
    }

    @Test
    void skipsSyncWhenTitleMissingInLegacyPayload() {
        // title 필드 추가 이전에 저장된 페이로드는 null로 역직렬화된다.
        handler.handle(outboxEvent(null));

        verify(liveSettingCapability, never()).updateLiveSetting(anyString(), any());
        verify(transactionService).activatePlatform(PLATFORM_SESSION_ID, NOW);
    }

    @Test
    void skipsSyncWhenPlatformLacksCapability() {
        when(adapter.liveSetting()).thenReturn(Optional.empty());

        handler.handle(outboxEvent("오늘 방송 제목"));

        verify(platformAccountService, never()).accessToken(anyLong(), anyLong());
        verify(transactionService).activatePlatform(PLATFORM_SESSION_ID, NOW);
    }

    private BroadcastOutboxEvent outboxEvent(String title) {
        UUID eventId = UUID.randomUUID();
        PlatformSessionPreparationRequestedEvent payload = new PlatformSessionPreparationRequestedEvent(
                eventId,
                (short) 1,
                SESSION_ID,
                PLATFORM_SESSION_ID,
                STREAMER_ID,
                PLATFORM_ACCOUNT_ID,
                "CHZZK",
                NOW,
                "ko",
                title,
                NOW
        );
        try {
            return new BroadcastOutboxEvent(
                    eventId,
                    SESSION_ID,
                    BroadcastOutboxEventType.PLATFORM_SESSION_PREPARATION_REQUESTED,
                    (short) 1,
                    "platform-session:" + PLATFORM_SESSION_ID + ":prepare",
                    objectMapper.writeValueAsString(payload),
                    0,
                    NOW
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

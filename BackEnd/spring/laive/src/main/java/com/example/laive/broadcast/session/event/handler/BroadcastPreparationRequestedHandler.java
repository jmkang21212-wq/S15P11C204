package com.example.laive.broadcast.session.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.BroadcastPreparationRequestedEvent;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * PREPARING 세션의 필수 컴포넌트를 초기화한 뒤 LIVE로 전환한다.
 */
@Component
public class BroadcastPreparationRequestedHandler implements BroadcastOutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final SessionRedisGateway redisGateway;
    private final ChatCollectorGateway chatCollectorGateway;
    private final AiSessionGateway aiSessionGateway;
    private final BroadcastSessionTransactionService transactionService;

    public BroadcastPreparationRequestedHandler(
            ObjectMapper objectMapper,
            SessionRedisGateway redisGateway,
            ChatCollectorGateway chatCollectorGateway,
            AiSessionGateway aiSessionGateway,
            BroadcastSessionTransactionService transactionService
    ) {
        this.objectMapper = objectMapper;
        this.redisGateway = redisGateway;
        this.chatCollectorGateway = chatCollectorGateway;
        this.aiSessionGateway = aiSessionGateway;
        this.transactionService = transactionService;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.PREPARATION_REQUESTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent outboxEvent) {
        BroadcastPreparationRequestedEvent event = read(outboxEvent);
        redisGateway.initialize(event.sessionId(), event.streamerId(), event.startedAt());
        chatCollectorGateway.start(
                event.sessionId(),
                event.streamerId(),
                event.platformAccountId()
        );
        aiSessionGateway.start(event.sessionId(), event.language());
        transactionService.activate(event.sessionId());
    }

    @Override
    public void onDead(BroadcastOutboxEvent event) {
        transactionService.failInitialization(event.aggregateId());
    }

    private BroadcastPreparationRequestedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(
                    event.payload(),
                    BroadcastPreparationRequestedEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("준비 이벤트 JSON을 읽을 수 없습니다.", exception);
        }
    }
}


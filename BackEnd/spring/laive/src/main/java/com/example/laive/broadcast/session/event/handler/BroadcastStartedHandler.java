package com.example.laive.broadcast.session.event.handler;

import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.BroadcastStartedEvent;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * LIVE 전환을 WebSocket 재연결용 Redis Stream에 기록한다.
 */
@Component
public class BroadcastStartedHandler implements BroadcastOutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final SessionRedisGateway redisGateway;

    public BroadcastStartedHandler(ObjectMapper objectMapper, SessionRedisGateway redisGateway) {
        this.objectMapper = objectMapper;
        this.redisGateway = redisGateway;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.BROADCAST_STARTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent outboxEvent) {
        BroadcastStartedEvent event = read(outboxEvent);
        redisGateway.publishStatus(
                event.sessionId(),
                BroadcastStatus.LIVE,
                event.occurredAt()
        );
    }

    private BroadcastStartedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(event.payload(), BroadcastStartedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("방송 시작 이벤트 JSON을 읽을 수 없습니다.", exception);
        }
    }
}


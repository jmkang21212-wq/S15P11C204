package com.example.laive.broadcast.session.event.handler;

import com.example.laive.broadcast.session.event.BroadcastEndedEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.service.BroadcastSessionFinalizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 방송 종료 후 수집기 Flush, 통계 확정과 리포트 생성을 수행한다.
 */
@Component
public class BroadcastEndedHandler implements BroadcastOutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final BroadcastSessionFinalizationService finalizationService;

    public BroadcastEndedHandler(
            ObjectMapper objectMapper,
            BroadcastSessionFinalizationService finalizationService
    ) {
        this.objectMapper = objectMapper;
        this.finalizationService = finalizationService;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.BROADCAST_ENDED;
    }

    @Override
    public void handle(BroadcastOutboxEvent outboxEvent) {
        finalizationService.finalizeSession(read(outboxEvent));
    }

    @Override
    public void onDead(BroadcastOutboxEvent outboxEvent) {
        finalizationService.markFailed(read(outboxEvent).reportId());
    }

    private BroadcastEndedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(event.payload(), BroadcastEndedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("방송 종료 이벤트 JSON을 읽을 수 없습니다.", exception);
        }
    }
}


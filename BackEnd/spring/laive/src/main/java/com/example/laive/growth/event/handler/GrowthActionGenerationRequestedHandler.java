package com.example.laive.growth.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.handler.BroadcastOutboxEventHandler;
import com.example.laive.growth.event.GrowthActionGenerationRequestedEvent;
import com.example.laive.growth.service.GrowthActionGenerationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Component
public class GrowthActionGenerationRequestedHandler implements BroadcastOutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final GrowthActionGenerationService service;
    private final Executor executor;

    public GrowthActionGenerationRequestedHandler(
            ObjectMapper objectMapper,
            GrowthActionGenerationService service,
            @Qualifier("analysisAiExecutor") Executor executor
    ) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.GROWTH_ACTION_GENERATION_REQUESTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent event) {
        var payload = read(event);
        try {
            CompletableFuture.runAsync(
                    () -> service.generate(payload.generationId()), executor
            ).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    @Override
    public void onDead(BroadcastOutboxEvent event) {
        service.dead(read(event).generationId(), "OUTBOX_DEAD");
    }

    private GrowthActionGenerationRequestedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(
                    event.payload(), GrowthActionGenerationRequestedEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("성장 액션 Outbox 페이로드가 올바르지 않습니다.", exception);
        }
    }
}

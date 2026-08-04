package com.example.laive.growth.roadmap.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.handler.BroadcastOutboxEventHandler;
import com.example.laive.growth.roadmap.event.GrowthRoadmapGenerationRequestedEvent;
import com.example.laive.growth.roadmap.service.GrowthRoadmapGenerationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Component
public class GrowthRoadmapGenerationRequestedHandler
        implements BroadcastOutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final GrowthRoadmapGenerationService service;
    private final Executor executor;

    public GrowthRoadmapGenerationRequestedHandler(
            ObjectMapper objectMapper,
            GrowthRoadmapGenerationService service,
            @Qualifier("analysisAiExecutor") Executor executor
    ) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.GROWTH_ROADMAP_GENERATION_REQUESTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent event) {
        var payload = read(event);
        try {
            CompletableFuture.runAsync(
                    () -> service.generate(
                            payload.generationId(), payload.streamerId(),
                            payload.promptVersion()
                    ),
                    executor
            ).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw exception;
        }
    }

    @Override
    public void onDead(BroadcastOutboxEvent event) {
        service.dead(read(event).generationId());
    }

    private GrowthRoadmapGenerationRequestedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(
                    event.payload(), GrowthRoadmapGenerationRequestedEvent.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid growth roadmap outbox payload.", exception);
        }
    }
}

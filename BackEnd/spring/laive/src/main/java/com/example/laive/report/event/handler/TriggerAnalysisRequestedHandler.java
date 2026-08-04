package com.example.laive.report.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.handler.BroadcastOutboxEventHandler;
import com.example.laive.report.event.TriggerAnalysisRequestedEvent;
import com.example.laive.report.service.TriggerAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;

@Component
public class TriggerAnalysisRequestedHandler implements BroadcastOutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final TriggerAnalysisService service;
    private final Executor executor;

    public TriggerAnalysisRequestedHandler(
            ObjectMapper objectMapper,
            TriggerAnalysisService service,
            @Qualifier("analysisAiExecutor") Executor executor
    ) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.TRIGGER_ANALYSIS_REQUESTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent event) {
        var payload = read(event);
        try {
            CompletableFuture.runAsync(() -> service.analyze(payload), executor).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    @Override
    public void onDead(BroadcastOutboxEvent event) {
        service.fail(read(event), "TriggerAnalysisException");
    }

    private TriggerAnalysisRequestedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(event.payload(), TriggerAnalysisRequestedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("트리거 분석 이벤트를 읽을 수 없습니다.", exception);
        }
    }
}

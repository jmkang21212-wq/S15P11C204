package com.example.laive.report.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.handler.BroadcastOutboxEventHandler;
import com.example.laive.report.event.ReportFinalizationRequestedEvent;
import com.example.laive.report.service.ReportCompletionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class ReportFinalizationRequestedHandler implements BroadcastOutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final ReportCompletionService service;
    private final Executor executor;

    public ReportFinalizationRequestedHandler(
            ObjectMapper objectMapper,
            ReportCompletionService service,
            @Qualifier("reportAiExecutor") Executor executor
    ) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public BroadcastOutboxEventType supports() {
        return BroadcastOutboxEventType.REPORT_FINALIZATION_REQUESTED;
    }

    @Override
    public void handle(BroadcastOutboxEvent event) {
        var payload = read(event);
        try {
            CompletableFuture.runAsync(() -> service.complete(payload), executor).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    @Override
    public void onDead(BroadcastOutboxEvent event) {
        service.partial(read(event));
    }

    private ReportFinalizationRequestedEvent read(BroadcastOutboxEvent event) {
        try {
            return objectMapper.readValue(event.payload(), ReportFinalizationRequestedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("리포트 완료 이벤트를 읽을 수 없습니다.", exception);
        }
    }
}

package com.example.laive.broadcast.collect.controller;

import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.dto.request.ChatMessageBatchRequest;
import com.example.laive.broadcast.collect.dto.request.CollectionGapRequest;
import com.example.laive.broadcast.collect.dto.request.CollectorStatusRequest;
import com.example.laive.broadcast.collect.dto.request.ViewerMetricBatchRequest;
import com.example.laive.broadcast.collect.dto.response.BatchIngestResponse;
import com.example.laive.broadcast.collect.dto.response.PlatformCollectorSpecResponse;
import com.example.laive.broadcast.collect.integration.CollectorStatusStore;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.collect.service.CollectionRelayService;
import com.example.laive.broadcast.collect.service.CollectorCredentialsService;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.exception.BusinessException;
import com.example.laive.internal.config.InternalApiProperties;
import com.example.laive.internal.idempotency.RequestIdGuard;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.broadcast.collect.domain.CollectorState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.function.Supplier;

/** Internal ingestion contract keyed by provider child session. */
@Validated
@RestController
@RequestMapping("/internal/v1/platform-sessions/{platformSessionId}")
public class InternalPlatformCollectionController {

    private final CollectionIngestService ingestService;
    private final CollectionRelayService relayService;
    private final CollectorCredentialsService credentialsService;
    private final CollectorStatusStore collectorStatusStore;
    private final RequestIdGuard requestIdGuard;
    private final InternalApiProperties properties;
    private final ObjectMapper objectMapper;
    private final BroadcastSessionTransactionService sessionTransactionService;

    public InternalPlatformCollectionController(
            CollectionIngestService ingestService,
            CollectionRelayService relayService,
            CollectorCredentialsService credentialsService,
            CollectorStatusStore collectorStatusStore,
            RequestIdGuard requestIdGuard,
            InternalApiProperties properties,
            ObjectMapper objectMapper,
            BroadcastSessionTransactionService sessionTransactionService
    ) {
        this.ingestService = ingestService;
        this.relayService = relayService;
        this.credentialsService = credentialsService;
        this.collectorStatusStore = collectorStatusStore;
        this.requestIdGuard = requestIdGuard;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionTransactionService = sessionTransactionService;
    }

    @PostMapping("/chat-messages:batch")
    public ResponseEntity<ApiResponse<BatchIngestResponse>> appendChatMessages(
            @PathVariable @Positive long platformSessionId,
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @Valid @RequestBody ChatMessageBatchRequest request
    ) {
        requireBatchWithinLimit(request.items().size());
        BatchIngestResponse response = executeIdempotently(
                requestId,
                BatchIngestResponse.class,
                () -> BatchIngestResponse.from(relayService.appendPlatformChatMessages(
                        platformSessionId, request.toDomain()))
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/viewer-metrics:batch")
    public ResponseEntity<ApiResponse<BatchIngestResponse>> appendViewerMetrics(
            @PathVariable @Positive long platformSessionId,
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @Valid @RequestBody ViewerMetricBatchRequest request
    ) {
        requireBatchWithinLimit(request.items().size());
        BatchIngestResponse response = executeIdempotently(
                requestId,
                BatchIngestResponse.class,
                () -> BatchIngestResponse.from(relayService.appendPlatformViewerMetrics(
                        platformSessionId, request.toDomain()))
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/collector-spec")
    public ResponseEntity<ApiResponse<PlatformCollectorSpecResponse>> collectorSpec(
            @PathVariable @Positive long platformSessionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                credentialsService.issuePlatformSession(platformSessionId)));
    }

    @PostMapping("/collector-status")
    public ResponseEntity<Void> reportCollectorStatus(
            @PathVariable @Positive long platformSessionId,
            @Valid @RequestBody CollectorStatusRequest request
    ) {
        long sessionId = ingestService.requirePlatformSession(platformSessionId).sessionId();
        collectorStatusStore.record(sessionId, request.status(), request.occurredAt());
        if (request.status() == CollectorState.STOPPED) {
            sessionTransactionService.endPlatform(platformSessionId, request.occurredAt());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/collection-gaps")
    public ResponseEntity<Void> reportCollectionGap(
            @PathVariable @Positive long platformSessionId,
            @Valid @RequestBody CollectionGapRequest request
    ) {
        relayService.recordPlatformCollectionGap(platformSessionId, request.toDomain());
        return ResponseEntity.noContent().build();
    }

    private void requireBatchWithinLimit(int size) {
        if (size > properties.maxBatchItems()) {
            throw new BusinessException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "INTERNAL_BATCH_TOO_LARGE",
                    "Batch exceeds limit: " + properties.maxBatchItems()
            );
        }
    }

    private <T> T executeIdempotently(String requestId, Class<T> type, Supplier<T> action) {
        Optional<String> recorded = requestIdGuard.begin(requestId);
        if (recorded.isPresent()) {
            return deserialize(recorded.get(), type);
        }
        try {
            T result = action.get();
            requestIdGuard.complete(requestId, serialize(result));
            return result;
        } catch (RuntimeException exception) {
            requestIdGuard.abandon(requestId);
            throw exception;
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize idempotent response", exception);
        }
    }

    private <T> T deserialize(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize idempotent response", exception);
        }
    }
}

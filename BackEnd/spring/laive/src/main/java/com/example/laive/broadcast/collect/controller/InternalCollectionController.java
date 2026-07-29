package com.example.laive.broadcast.collect.controller;

import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.dto.request.ChatMessageBatchRequest;
import com.example.laive.broadcast.collect.dto.request.CollectionGapRequest;
import com.example.laive.broadcast.collect.dto.request.CollectorStatusRequest;
import com.example.laive.broadcast.collect.dto.request.ViewerMetricBatchRequest;
import com.example.laive.broadcast.collect.dto.response.BatchIngestResponse;
import com.example.laive.broadcast.collect.dto.response.CollectorCredentialsResponse;
import com.example.laive.broadcast.collect.integration.CollectorStatusStore;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.collect.service.CollectorCredentialsService;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.exception.BusinessException;
import com.example.laive.internal.config.InternalApiProperties;
import com.example.laive.internal.idempotency.RequestIdGuard;
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

/**
 * 수집기가 보내는 원천 데이터를 받는 내부 API다(명세 13.3).
 *
 * <p>인증은 서비스 토큰이며 필터 체인은 {@code InternalSecurityConfig}에 있다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/sessions/{sessionId}")
public class InternalCollectionController {

    private final CollectionIngestService ingestService;
    private final CollectorCredentialsService credentialsService;
    private final CollectorStatusStore collectorStatusStore;
    private final RequestIdGuard requestIdGuard;
    private final InternalApiProperties properties;
    private final ObjectMapper objectMapper;

    public InternalCollectionController(
            CollectionIngestService ingestService,
            CollectorCredentialsService credentialsService,
            CollectorStatusStore collectorStatusStore,
            RequestIdGuard requestIdGuard,
            InternalApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.ingestService = ingestService;
        this.credentialsService = credentialsService;
        this.collectorStatusStore = collectorStatusStore;
        this.requestIdGuard = requestIdGuard;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 채팅 메시지를 배치 저장한다(명세 13.3.1). */
    @PostMapping("/chat-messages:batch")
    public ResponseEntity<ApiResponse<BatchIngestResponse>> appendChatMessages(
            @PathVariable @Positive long sessionId,
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @Valid @RequestBody ChatMessageBatchRequest request
    ) {
        requireBatchWithinLimit(request.items().size());
        BatchIngestResponse response = executeIdempotently(
                requestId,
                BatchIngestResponse.class,
                () -> {
                    BatchIngestResult result =
                            ingestService.appendChatMessages(sessionId, request.toDomain());
                    return BatchIngestResponse.from(result);
                }
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 시청자 수 지표를 배치 저장한다(명세 13.3.2). */
    @PostMapping("/viewer-metrics:batch")
    public ResponseEntity<ApiResponse<BatchIngestResponse>> appendViewerMetrics(
            @PathVariable @Positive long sessionId,
            @RequestHeader("X-Request-Id") @NotBlank String requestId,
            @Valid @RequestBody ViewerMetricBatchRequest request
    ) {
        requireBatchWithinLimit(request.items().size());
        BatchIngestResponse response = executeIdempotently(
                requestId,
                BatchIngestResponse.class,
                () -> {
                    BatchIngestResult result =
                            ingestService.appendViewerMetrics(sessionId, request.toDomain());
                    return BatchIngestResponse.from(result);
                }
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 수집기가 치지직 핸드셰이크에 쓸 자격 정보를 발급한다.
     *
     * <p>조회 요청이라 X-Request-Id 멱등 처리 대상이 아니다(명세 13.1은 변경 요청에 건다).
     */
    @GetMapping("/collector-credentials")
    public ResponseEntity<ApiResponse<CollectorCredentialsResponse>> collectorCredentials(
            @PathVariable @Positive long sessionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(credentialsService.issue(sessionId)));
    }

    /**
     * 수집기 연결 상태를 보고한다. 하트비트를 겸한다.
     *
     * <p>X-Request-Id를 받지 않는다. 최신값 하나만 남기는 덮어쓰기라 재시도가
     * 같은 결과로 수렴하고, 초 단위 하트비트마다 멱등 키를 Redis에 쌓는 비용만
     * 남는다.
     */
    @PostMapping("/collector-status")
    public ResponseEntity<Void> reportCollectorStatus(
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody CollectorStatusRequest request
    ) {
        collectorStatusStore.record(sessionId, request.status(), request.occurredAt());
        return ResponseEntity.noContent().build();
    }

    /**
     * 수집 공백 구간을 보고한다.
     *
     * <p>X-Request-Id를 받지 않는다. (session_id, started_at) Upsert라 같은 보고를
     * 몇 번 보내도 행이 늘지 않는다(V9).
     */
    @PostMapping("/collection-gaps")
    public ResponseEntity<Void> reportCollectionGap(
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody CollectionGapRequest request
    ) {
        ingestService.recordCollectionGap(sessionId, request.toDomain());
        return ResponseEntity.noContent().build();
    }

    private void requireBatchWithinLimit(int size) {
        if (size > properties.maxBatchItems()) {
            throw new BusinessException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "INTERNAL_BATCH_TOO_LARGE",
                    "배치 항목 수가 허용 한도(" + properties.maxBatchItems() + ")를 초과했습니다."
            );
        }
    }

    /**
     * 같은 X-Request-Id의 재시도가 두 번 저장되지 않게 한다(명세 13.1).
     *
     * <p>실패하면 선점을 반드시 되돌린다. 표식을 남겨두면 그 requestId는 TTL이
     * 끝날 때까지 409로 막혀, 일시적 실패가 영구 실패가 된다.
     */
    private <T> T executeIdempotently(String requestId, Class<T> type, Supplier<T> action) {
        Optional<String> recorded = requestIdGuard.begin(requestId);
        if (recorded.isPresent()) {
            return deserialize(recorded.get(), type);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException exception) {
            requestIdGuard.abandon(requestId);
            throw exception;
        }
        requestIdGuard.complete(requestId, serialize(result));
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("멱등 응답을 직렬화할 수 없습니다.", exception);
        }
    }

    private <T> T deserialize(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("멱등 응답을 읽을 수 없습니다.", exception);
        }
    }
}

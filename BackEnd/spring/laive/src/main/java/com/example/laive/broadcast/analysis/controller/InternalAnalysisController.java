package com.example.laive.broadcast.analysis.controller;

import com.example.laive.broadcast.analysis.dto.request.PromiseDetectionRequest;
import com.example.laive.broadcast.analysis.dto.request.RiskAlertRequest;
import com.example.laive.broadcast.analysis.dto.request.TranscriptBatchRequest;
import com.example.laive.broadcast.analysis.dto.response.AnalysisIngestResponse;
import com.example.laive.broadcast.analysis.service.AnalysisIngestService;
import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.dto.response.BatchIngestResponse;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.exception.BusinessException;
import com.example.laive.internal.config.InternalApiProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 서버가 보내는 분석 결과를 받는 내부 API다(명세 13.4, 13.5).
 *
 * <p>인증은 서비스 토큰이며 필터 체인은 {@code InternalSecurityConfig}에 있다.
 *
 * <p>수집기 API와 달리 X-Request-Id 멱등 가드를 걸지 않는다. 전사는
 * (session_id, start_ms, end_ms) Unique Upsert라 재전송이 같은 결과로 수렴하고,
 * 위험 발언·약속은 실시간 경고라 한 건씩 즉시 들어오며 재시도로 중복이 생겨도
 * 스트리머가 같은 경고를 두 번 보는 정도라 저장을 막을 이유가 없다.
 */
@Validated
@RestController
@RequestMapping("/internal/v1/sessions/{sessionId}")
public class InternalAnalysisController {

    private final AnalysisIngestService ingestService;
    private final InternalApiProperties properties;

    public InternalAnalysisController(
            AnalysisIngestService ingestService,
            InternalApiProperties properties
    ) {
        this.ingestService = ingestService;
        this.properties = properties;
    }

    /** STT 전사를 배치 저장한다(명세 13.4). */
    @PostMapping("/transcripts:batch")
    public ResponseEntity<ApiResponse<BatchIngestResponse>> appendTranscripts(
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody TranscriptBatchRequest request
    ) {
        requireBatchWithinLimit(request.items().size());
        BatchIngestResult result = ingestService.appendTranscripts(sessionId, request.toDomain());
        return ResponseEntity.ok(ApiResponse.success(BatchIngestResponse.from(result)));
    }

    /** 위험 발언을 저장하고 RISK 브리핑을 만든다(명세 13.5). */
    @PostMapping("/risk-events")
    public ResponseEntity<ApiResponse<AnalysisIngestResponse>> appendRiskEvent(
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody RiskAlertRequest request
    ) {
        long id = ingestService.appendRiskEvent(sessionId, request.toDomain());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(new AnalysisIngestResponse(id)));
    }

    /** 약속 후보를 DETECTED로 저장하고 PROMISE 브리핑을 만든다(명세 13.5). */
    @PostMapping("/promises")
    public ResponseEntity<ApiResponse<AnalysisIngestResponse>> appendPromise(
            @PathVariable @Positive long sessionId,
            @Valid @RequestBody PromiseDetectionRequest request
    ) {
        long id = ingestService.appendPromise(sessionId, request.toDomain());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(new AnalysisIngestResponse(id)));
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
}

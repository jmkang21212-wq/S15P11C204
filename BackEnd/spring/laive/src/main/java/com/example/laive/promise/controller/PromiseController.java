package com.example.laive.promise.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.api.PageResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseStatus;
import com.example.laive.promise.dto.request.ConfirmPromiseRequest;
import com.example.laive.promise.dto.request.UpdatePromiseRequest;
import com.example.laive.promise.dto.response.PromiseResponse;
import com.example.laive.promise.service.PromiseCommandService;
import com.example.laive.promise.service.PromiseQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI가 감지한 약속을 스트리머가 확정·무시·관리하는 API다 (명세 10.8).
 *
 * <p>약속 자체는 AI 수신부(broadcast.analysis)가 DETECTED로 저장한다. 이 컨트롤러는
 * 그 뒤의 사람 결정을 다룬다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/promises")
public class PromiseController {

    private final PromiseQueryService queryService;
    private final PromiseCommandService commandService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public PromiseController(
            PromiseQueryService queryService,
            PromiseCommandService commandService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /** 로그인 스트리머의 약속을 상태 필터와 0 기반 페이지로 조회한다. status 생략 시 전체다. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PromiseResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) PromiseStatus status
    ) {
        PageResponse<Promise> result = queryService.getPromises(
                streamerIdExtractor.extract(jwt), status, page, size);

        List<PromiseResponse> items = result.items().stream()
                .map(PromiseResponse::from)
                .toList();
        PageResponse<PromiseResponse> response = new PageResponse<>(
                items, result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 감지된 약속을 확정한다 (API 10.8.2).
     *
     * <p>사용자가 고친 내용·기한을 반영하고 PENDING으로 옮긴다. 기한을 넣으면 그 시점부터
     * 캘린더에 표시된다. 현재 상태가 DETECTED가 아니면 409다.
     */
    @PostMapping("/{promiseId}/confirm")
    public ResponseEntity<ApiResponse<PromiseResponse>> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long promiseId,
            @Valid @RequestBody ConfirmPromiseRequest request
    ) {
        Promise promise = commandService.confirm(
                streamerIdExtractor.extract(jwt), promiseId, request.content(), request.dueDate());
        return ResponseEntity.ok(ApiResponse.success(PromiseResponse.from(promise)));
    }

    /** 감지된 약속을 무시한다 (API 10.8.2). 현재 상태가 DETECTED가 아니면 409다. */
    @PostMapping("/{promiseId}/dismiss")
    public ResponseEntity<Void> dismiss(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long promiseId
    ) {
        commandService.dismiss(streamerIdExtractor.extract(jwt), promiseId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 확정된 약속의 상태·내용·기한을 부분 수정한다 (API 10.8.3 확장).
     *
     * <p>캘린더에서 약속을 끌어 옮기면 프론트가 {@code {"dueDate": "..."}} 하나만 보낸다.
     * 상태 변경·상세 수정도 같은 엔드포인트를 쓴다.
     */
    @PatchMapping("/{promiseId}")
    public ResponseEntity<ApiResponse<PromiseResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long promiseId,
            @Valid @RequestBody UpdatePromiseRequest request
    ) {
        Promise promise = commandService.update(
                streamerIdExtractor.extract(jwt), promiseId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.success(PromiseResponse.from(promise)));
    }
}

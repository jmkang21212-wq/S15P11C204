package com.example.laive.streamer.risk.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.streamer.risk.dto.request.CreateRiskTermRequest;
import com.example.laive.streamer.risk.dto.response.RiskTermsResponse;
import com.example.laive.streamer.risk.service.StreamerRiskTermService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 스트리머 본인의 위험 발언 개인 사전 API다 (AI/RISK_POLICY_SPEC.md §5).
 *
 * <p>모든 응답이 갱신된 전체 목록이라 프론트가 등록·삭제 후 다시 조회할 필요가 없다.
 * 목록이 최대 400개(종류당 200)로 묶여 있어 매번 내려도 부담이 없다.
 *
 * <p>방송 중에 고쳐도 즉시 반영된다 — 서비스가 커밋 후 Redis로 알리고 AI 서버가
 * 살아있는 세션의 사전을 갈아끼운다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/streamers/me/risk-terms")
public class StreamerRiskTermController {

    private final StreamerRiskTermService service;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public StreamerRiskTermController(
            StreamerRiskTermService service,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.service = service;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RiskTermsResponse>> list(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(RiskTermsResponse.from(
                service.list(streamerIdExtractor.extract(jwt)))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RiskTermsResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRiskTermRequest request
    ) {
        RiskTermsResponse body = RiskTermsResponse.from(service.register(
                streamerIdExtractor.extract(jwt),
                request.kind(),
                request.term(),
                request.severity()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<RiskTermsResponse>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(RiskTermsResponse.from(
                service.remove(streamerIdExtractor.extract(jwt), id))));
    }
}

package com.example.laive.growth.roadmap.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.growth.dto.response.GrowthGoalResponse;
import com.example.laive.growth.roadmap.dto.GrowthRoadmapGenerationResponse;
import com.example.laive.growth.roadmap.dto.GrowthRoadmapResponse;
import com.example.laive.growth.roadmap.service.GrowthRoadmapQueryService;
import com.example.laive.growth.roadmap.service.GrowthRoadmapTransactionService;
import com.example.laive.growth.roadmap.service.GrowthSuggestionCommandService;
import com.example.laive.growth.service.GrowthGoalQueryService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/growth-roadmap")
public class GrowthRoadmapController {
    private final GrowthRoadmapQueryService queryService;
    private final GrowthRoadmapTransactionService transactions;
    private final GrowthSuggestionCommandService suggestionCommands;
    private final GrowthGoalQueryService goalQueryService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public GrowthRoadmapController(
            GrowthRoadmapQueryService queryService,
            GrowthRoadmapTransactionService transactions,
            GrowthSuggestionCommandService suggestionCommands,
            GrowthGoalQueryService goalQueryService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.queryService = queryService;
        this.transactions = transactions;
        this.suggestionCommands = suggestionCommands;
        this.goalQueryService = goalQueryService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GrowthRoadmapResponse>> get(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        var response = queryService.find(streamerId)
                .map(GrowthRoadmapResponse::from)
                .orElseGet(GrowthRoadmapResponse::notRequested);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<ApiResponse<GrowthRoadmapGenerationResponse>> regenerate(
            @AuthenticationPrincipal Jwt jwt
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        var generation = transactions.requestManual(streamerId);
        return ResponseEntity.accepted().body(ApiResponse.success(
                GrowthRoadmapGenerationResponse.from(generation)
        ));
    }

    @PostMapping("/suggestions/{suggestionId}/accept")
    public ResponseEntity<ApiResponse<GrowthGoalResponse>> accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long suggestionId
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        var accepted = suggestionCommands.accept(streamerId, suggestionId);
        var response = GrowthGoalResponse.from(
                goalQueryService.getGoal(streamerId, accepted.goalId())
        );
        return ResponseEntity.status(
                accepted.created() ? HttpStatus.CREATED : HttpStatus.OK
        ).body(ApiResponse.success(response));
    }

    @PostMapping("/suggestions/{suggestionId}/dismiss")
    public ResponseEntity<Void> dismiss(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long suggestionId
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        suggestionCommands.dismiss(streamerId, suggestionId);
        return ResponseEntity.noContent().build();
    }
}

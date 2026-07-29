package com.example.laive.growth.controller;

import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import com.example.laive.growth.domain.GrowthGoalStatus;
import com.example.laive.growth.dto.request.CreateGrowthGoalRequest;
import com.example.laive.growth.dto.request.UpdateGrowthGoalRequest;
import com.example.laive.growth.dto.response.GrowthGoalListResponse;
import com.example.laive.growth.dto.response.GrowthGoalResponse;
import com.example.laive.growth.service.GrowthGoalCommandService;
import com.example.laive.growth.service.GrowthGoalQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
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

@Validated
@RestController
@RequestMapping("/api/v1/growth-goals")
public class GrowthGoalController {
    private final GrowthGoalQueryService queryService;
    private final GrowthGoalCommandService commandService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public GrowthGoalController(
            GrowthGoalQueryService queryService,
            GrowthGoalCommandService commandService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<GrowthGoalListResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) GrowthGoalStatus status
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        var items = queryService.getGoals(streamerId, status)
                .stream()
                .map(GrowthGoalResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(new GrowthGoalListResponse(items)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GrowthGoalResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateGrowthGoalRequest request
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        long id = commandService.create(streamerId, request.toCommand());
        var response = GrowthGoalResponse.from(queryService.getGoal(streamerId, id));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PatchMapping("/{growthGoalId}")
    public ResponseEntity<ApiResponse<GrowthGoalResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long growthGoalId,
            @Valid @RequestBody UpdateGrowthGoalRequest request
    ) {
        long streamerId = streamerIdExtractor.extract(jwt);
        commandService.update(streamerId, growthGoalId, request.toCommand());
        var response = GrowthGoalResponse.from(
                queryService.getGoal(streamerId, growthGoalId)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

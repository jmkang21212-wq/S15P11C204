package com.example.laive.broadcast.session.controller;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.domain.BroadcastSessionListItem;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.dto.request.StartBroadcastSessionRequest;
import com.example.laive.broadcast.session.dto.response.BroadcastSessionDetailResponse;
import com.example.laive.broadcast.session.dto.response.BroadcastSessionListItemResponse;
import com.example.laive.broadcast.session.dto.response.EndBroadcastSessionResponse;
import com.example.laive.broadcast.session.dto.response.StartBroadcastSessionResponse;
import com.example.laive.broadcast.session.dto.response.VoiceTicketResponse;
import com.example.laive.broadcast.session.service.BroadcastSessionCommandService;
import com.example.laive.broadcast.session.service.BroadcastSessionQueryService;
import com.example.laive.broadcast.session.service.VoiceTicketService;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.api.PageResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 방송 시작·종료와 현재/이력/상세 조회 API를 제공한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/broadcast-sessions")
public class BroadcastSessionController {

    private final BroadcastSessionCommandService commandService;
    private final BroadcastSessionQueryService queryService;
    private final VoiceTicketService voiceTicketService;
    private final BroadcastSessionProperties properties;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public BroadcastSessionController(
            BroadcastSessionCommandService commandService,
            BroadcastSessionQueryService queryService,
            VoiceTicketService voiceTicketService,
            BroadcastSessionProperties properties,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.voiceTicketService = voiceTicketService;
        this.properties = properties;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /** PREPARING 세션과 준비 Outbox 이벤트를 생성한다. */
    @PostMapping
    public ResponseEntity<ApiResponse<StartBroadcastSessionResponse>> start(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartBroadcastSessionRequest request
    ) {
        var result = commandService.start(
                streamerIdExtractor.extract(jwt),
                request.toCommand()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(StartBroadcastSessionResponse.from(result)));
    }

    /** PREPARING 또는 LIVE인 본인 세션을 종료하고 리포트 작업을 예약한다. */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<ApiResponse<EndBroadcastSessionResponse>> end(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId
    ) {
        var result = commandService.end(streamerIdExtractor.extract(jwt), sessionId);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(EndBroadcastSessionResponse.from(result)));
    }

    /**
     * 음성 AI WebSocket 접속용 일회용 티켓을 발급한다.
     *
     * <p>WebSocket은 브라우저에서 Authorization 헤더를 실을 수 없어 JWT를 직접 못 쓴다.
     * 인증된 이 요청으로 티켓을 받아 핸드셰이크에 실으면, AI 서버가 Redis에서 조회해
     * <b>어느 방송 세션인지를 티켓에서</b> 알아낸다 — 클라이언트가 주장하는 번호를
     * 믿지 않으므로 남의 방송에 기록하는 경로가 막힌다.
     *
     * <p>일회용이고 짧게 만료되므로 재연결할 때마다 새로 호출해야 한다.
     */
    @PostMapping("/{sessionId}/voice-ticket")
    public ResponseEntity<ApiResponse<VoiceTicketResponse>> issueVoiceTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId
    ) {
        String ticket = voiceTicketService.issue(streamerIdExtractor.extract(jwt), sessionId);
        return ResponseEntity.ok(ApiResponse.success(
                new VoiceTicketResponse(ticket, properties.voiceTicketTtl().toSeconds())));
    }

    /** PREPARING 또는 LIVE인 현재 세션을 반환하며 없으면 204를 반환한다. */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<BroadcastSessionDetailResponse>> current(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return queryService.getCurrent(streamerIdExtractor.extract(jwt))
                .map(detail -> BroadcastSessionDetailResponse.from(
                        detail, queryService.getPlatformSessions(detail.sessionId())))
                .map(ApiResponse::success)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 로그인 스트리머의 방송 이력을 상태 필터와 0 기반 페이지로 조회한다. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BroadcastSessionListItemResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) BroadcastStatus status
    ) {
        PageResponse<BroadcastSessionListItem> result = queryService.getSessions(
                streamerIdExtractor.extract(jwt),
                status,
                page,
                size
        );
        List<BroadcastSessionListItemResponse> items = result.items().stream()
                .map(BroadcastSessionListItemResponse::from)
                .toList();
        PageResponse<BroadcastSessionListItemResponse> response = new PageResponse<>(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** ID와 로그인 streamerId를 함께 조건으로 사용해 타인의 세션 존재를 숨긴다. */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<BroadcastSessionDetailResponse>> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long sessionId
    ) {
        var detail = queryService.getSession(streamerIdExtractor.extract(jwt), sessionId);
        return ResponseEntity.ok(ApiResponse.success(BroadcastSessionDetailResponse.from(
                detail, queryService.getPlatformSessions(detail.sessionId()))));
    }
}


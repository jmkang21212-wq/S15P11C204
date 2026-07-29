package com.example.laive.calendar.controller;

import com.example.laive.calendar.dto.request.CreateCalendarEventRequest;
import com.example.laive.calendar.dto.request.UpdateCalendarEventRequest;
import com.example.laive.calendar.dto.response.CalendarEventListResponse;
import com.example.laive.calendar.dto.response.CalendarEventResponse;
import com.example.laive.calendar.service.CalendarEventCommandService;
import com.example.laive.calendar.service.CalendarEventQueryService;
import com.example.laive.common.api.ApiResponse;
import com.example.laive.common.security.JwtStreamerIdExtractor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/calendar-events")
public class CalendarEventController {

    private final CalendarEventCommandService commandService;
    private final CalendarEventQueryService queryService;
    private final JwtStreamerIdExtractor streamerIdExtractor;

    public CalendarEventController(
            CalendarEventCommandService commandService,
            CalendarEventQueryService queryService,
            JwtStreamerIdExtractor streamerIdExtractor
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.streamerIdExtractor = streamerIdExtractor;
    }

    /** 월간·주간 캘린더에 표시할 일정을 조회한다. 범위는 최대 62일이다. */
    @GetMapping
    public ResponseEntity<ApiResponse<CalendarEventListResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        var events = queryService.getEvents(streamerIdExtractor.extract(jwt), from, to);
        return ResponseEntity.ok(ApiResponse.success(CalendarEventListResponse.from(events)));
    }

    /** SCHEDULED 상태의 새 일정을 만든다. */
    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCalendarEventRequest request
    ) {
        var event = commandService.create(streamerIdExtractor.extract(jwt), request.toCommand());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(CalendarEventResponse.from(event)));
    }

    /** ID와 로그인 streamerId를 함께 조건으로 사용해 타인의 일정 존재를 숨긴다. */
    @GetMapping("/{calendarEventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> detail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long calendarEventId
    ) {
        var event = queryService.getEvent(streamerIdExtractor.extract(jwt), calendarEventId);
        return ResponseEntity.ok(ApiResponse.success(CalendarEventResponse.from(event)));
    }

    /** 일정 필드를 부분 수정한다. 취소는 status = CANCELLED로 처리한다. */
    @PatchMapping("/{calendarEventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long calendarEventId,
            @RequestBody UpdateCalendarEventRequest request
    ) {
        var event = commandService.update(streamerIdExtractor.extract(jwt), calendarEventId, request.toCommand());
        return ResponseEntity.ok(ApiResponse.success(CalendarEventResponse.from(event)));
    }
}

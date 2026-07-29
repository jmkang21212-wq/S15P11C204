package com.example.laive.calendar.dto.response;

import com.example.laive.calendar.domain.CalendarEvent;

import java.util.List;

/**
 * 기간 조회 응답이다. 범위를 62일로 제한해 전량 반환하므로 페이지 필드가 없다 (API 8.1).
 */
public record CalendarEventListResponse(List<CalendarEventResponse> items) {

    public static CalendarEventListResponse from(List<CalendarEvent> events) {
        return new CalendarEventListResponse(
                events.stream()
                        .map(CalendarEventResponse::from)
                        .toList()
        );
    }
}

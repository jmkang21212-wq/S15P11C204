package com.example.laive.calendar.dto.response;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.dto.response.PromiseCalendarItemResponse;

import java.util.List;

/**
 * 기간 조회 응답이다. 범위를 62일로 제한해 전량 반환하므로 페이지 필드가 없다 (API 8.1).
 *
 * <p>items는 일정(calendar_events)이고 promises는 기한이 잡힌 진행 중 약속이다.
 * 약속은 일정 테이블에 복사하지 않고 조회 시점에 얹는다 — 복사하면 같은 약속이 두 행이 되어
 * 상태가 갈라지기 때문이다(약속은 COMPLETED인데 일정은 SCHEDULED로 남는 식).
 * 두 목록을 분리해 두면 items만 읽던 기존 소비자가 영향을 받지 않는다.
 */
public record CalendarEventListResponse(
        List<CalendarEventResponse> items,
        List<PromiseCalendarItemResponse> promises
) {
    public static CalendarEventListResponse from(
            List<CalendarEvent> events,
            List<PromiseCalendarItem> promises
    ) {
        return new CalendarEventListResponse(
                events.stream().map(CalendarEventResponse::from).toList(),
                promises.stream().map(PromiseCalendarItemResponse::from).toList()
        );
    }
}

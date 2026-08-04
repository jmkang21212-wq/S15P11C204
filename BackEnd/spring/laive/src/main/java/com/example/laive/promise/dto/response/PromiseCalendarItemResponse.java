package com.example.laive.promise.dto.response;

import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;

import java.time.LocalDate;

/**
 * 캘린더 화면에 얹을 약속 한 건이다.
 *
 * <p>일정(CalendarEventResponse)과 달리 시각이 없고 날짜만 있다. 프론트는 종일 항목처럼
 * 그리면 되고, 날짜를 옮기려면 {@code PATCH /promises/{id}}에 dueDate를 보내면 된다.
 */
public record PromiseCalendarItemResponse(
        long promiseId,
        String content,
        LocalDate dueDate,
        PromiseStatus status,
        Long calendarEventId
) {
    public static PromiseCalendarItemResponse from(PromiseCalendarItem item) {
        return new PromiseCalendarItemResponse(
                item.promiseId(),
                item.content(),
                item.dueDate(),
                item.status(),
                item.calendarEventId()
        );
    }
}

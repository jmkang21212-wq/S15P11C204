package com.example.laive.calendar.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 일정 캘린더 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class CalendarEventException extends BusinessException {

    private CalendarEventException(CalendarEventErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    public static CalendarEventException notFound() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_EVENT_NOT_FOUND);
    }

    public static CalendarEventException invalidPeriod() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_EVENT_INVALID_PERIOD);
    }

    public static CalendarEventException invalidTitle() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_EVENT_INVALID_TITLE);
    }

    public static CalendarEventException invalidField() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_EVENT_INVALID_FIELD);
    }

    public static CalendarEventException platformAccountRequired() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_EVENT_PLATFORM_REQUIRED);
    }

    public static CalendarEventException linkedToSession() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_EVENT_LINKED_TO_SESSION);
    }

    public static CalendarEventException rangeInvalid() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_RANGE_INVALID);
    }

    public static CalendarEventException rangeTooWide() {
        return new CalendarEventException(CalendarEventErrorCode.CALENDAR_RANGE_TOO_WIDE);
    }

    public static CalendarEventException platformAccountNotFound() {
        return new CalendarEventException(CalendarEventErrorCode.PLATFORM_ACCOUNT_NOT_FOUND);
    }

    public static CalendarEventException platformAccountNotConnected() {
        return new CalendarEventException(CalendarEventErrorCode.PLATFORM_ACCOUNT_NOT_CONNECTED);
    }
}

package com.example.laive.calendar.exception;

import org.springframework.http.HttpStatus;

/**
 * 일정 캘린더 API에서 외부에 공개하는 안정적인 오류 코드다.
 */
public enum CalendarEventErrorCode {
    CALENDAR_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "일정을 찾을 수 없습니다."
    ),
    CALENDAR_EVENT_INVALID_PERIOD(
            HttpStatus.BAD_REQUEST,
            "일정 종료 시각은 시작 시각보다 늦어야 합니다."
    ),
    CALENDAR_EVENT_INVALID_TITLE(
            HttpStatus.BAD_REQUEST,
            "일정 제목은 1자 이상 200자 이하여야 합니다."
    ),
    CALENDAR_EVENT_INVALID_FIELD(
            HttpStatus.BAD_REQUEST,
            "일정 유형과 상태는 비울 수 없습니다."
    ),
    CALENDAR_EVENT_PLATFORM_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "방송 일정에는 플랫폼 계정이 필요합니다."
    ),
    CALENDAR_EVENT_LINKED_TO_SESSION(
            HttpStatus.CONFLICT,
            "방송 세션과 연결된 일정은 시간·플랫폼·유형을 변경할 수 없습니다."
    ),
    CALENDAR_RANGE_INVALID(
            HttpStatus.BAD_REQUEST,
            "조회 종료 시각은 시작 시각보다 늦어야 합니다."
    ),
    CALENDAR_RANGE_TOO_WIDE(
            HttpStatus.BAD_REQUEST,
            "일정 조회 범위는 최대 62일입니다."
    ),
    PLATFORM_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "플랫폼 계정을 찾을 수 없습니다."
    ),
    PLATFORM_ACCOUNT_NOT_CONNECTED(
            HttpStatus.CONFLICT,
            "연결 상태가 아닌 플랫폼 계정입니다."
    );

    private final HttpStatus status;
    private final String message;

    CalendarEventErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}

package com.example.laive.broadcast.session.exception;

import org.springframework.http.HttpStatus;

/**
 * 방송 세션 API에서 외부에 공개하는 안정적인 오류 코드다.
 */
public enum BroadcastSessionErrorCode {
    PLATFORM_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "플랫폼 계정을 찾을 수 없습니다."
    ),
    PLATFORM_REAUTH_REQUIRED(
            HttpStatus.CONFLICT,
            "플랫폼 계정을 다시 연동해야 합니다."
    ),
    PLATFORM_CONNECTION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "플랫폼 연결 상태가 올바르지 않습니다."
    ),
    CALENDAR_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "방송 일정을 찾을 수 없습니다."
    ),
    CALENDAR_EVENT_STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "방송 일정의 상태 또는 플랫폼 계정이 요청과 일치하지 않습니다."
    ),
    SESSION_ALREADY_LIVE(
            HttpStatus.CONFLICT,
            "이미 진행 중인 방송 세션이 있습니다."
    ),
    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "방송 세션을 찾을 수 없습니다."
    ),
    SESSION_ALREADY_ENDED(
            HttpStatus.CONFLICT,
            "이미 종료된 방송 세션입니다."
    ),
    SESSION_STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "현재 상태에서는 요청한 방송 세션 작업을 수행할 수 없습니다."
    ),
    SESSION_INVALID_PERIOD(
            HttpStatus.BAD_REQUEST,
            "방송 종료 시각은 시작 시각보다 빠를 수 없습니다."
    );

    private final HttpStatus status;
    private final String message;

    BroadcastSessionErrorCode(HttpStatus status, String message) {
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


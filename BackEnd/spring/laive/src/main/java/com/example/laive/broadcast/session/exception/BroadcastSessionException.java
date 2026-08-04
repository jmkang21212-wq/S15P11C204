package com.example.laive.broadcast.session.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 방송 세션 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class BroadcastSessionException extends BusinessException {

    private BroadcastSessionException(BroadcastSessionErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    public static BroadcastSessionException platformAccountNotFound() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.PLATFORM_ACCOUNT_NOT_FOUND);
    }

    public static BroadcastSessionException platformReauthRequired() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.PLATFORM_REAUTH_REQUIRED);
    }

    public static BroadcastSessionException platformConnectionFailed() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.PLATFORM_CONNECTION_FAILED);
    }

    public static BroadcastSessionException calendarEventNotFound() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.CALENDAR_EVENT_NOT_FOUND);
    }

    public static BroadcastSessionException calendarEventStateConflict() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.CALENDAR_EVENT_STATE_CONFLICT);
    }

    public static BroadcastSessionException alreadyLive() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.SESSION_ALREADY_LIVE);
    }

    public static BroadcastSessionException notFound() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.SESSION_NOT_FOUND);
    }

    public static BroadcastSessionException alreadyEnded() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.SESSION_ALREADY_ENDED);
    }

    public static BroadcastSessionException stateConflict() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.SESSION_STATE_CONFLICT);
    }

    public static BroadcastSessionException invalidPeriod() {
        return new BroadcastSessionException(BroadcastSessionErrorCode.SESSION_INVALID_PERIOD);
    }
}


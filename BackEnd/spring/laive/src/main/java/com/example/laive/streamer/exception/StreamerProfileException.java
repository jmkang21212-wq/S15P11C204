package com.example.laive.streamer.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 프로필 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class StreamerProfileException extends BusinessException {

    private StreamerProfileException(StreamerProfileErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    private StreamerProfileException(StreamerProfileErrorCode errorCode, String message) {
        super(errorCode.status(), errorCode.name(), message);
    }

    public static StreamerProfileException notFound() {
        return new StreamerProfileException(StreamerProfileErrorCode.STREAMER_NOT_FOUND);
    }

    public static StreamerProfileException emptyPatch() {
        return new StreamerProfileException(StreamerProfileErrorCode.STREAMER_EMPTY_PATCH);
    }

    /** 어떤 필드가 왜 거절됐는지 메시지로 전달한다. 코드는 하나로 안정적으로 유지한다. */
    public static StreamerProfileException invalidField(String message) {
        return new StreamerProfileException(StreamerProfileErrorCode.STREAMER_INVALID_FIELD, message);
    }

    public static StreamerProfileException invalidTag() {
        return new StreamerProfileException(StreamerProfileErrorCode.STREAMER_INVALID_TAG);
    }
}

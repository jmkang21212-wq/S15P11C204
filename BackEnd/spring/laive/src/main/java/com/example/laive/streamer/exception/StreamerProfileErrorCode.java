package com.example.laive.streamer.exception;

import org.springframework.http.HttpStatus;

/**
 * 프로필 API가 외부에 공개하는 안정적인 오류 코드다.
 */
public enum StreamerProfileErrorCode {
    STREAMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "스트리머를 찾을 수 없습니다."
    ),
    STREAMER_EMPTY_PATCH(
            HttpStatus.BAD_REQUEST,
            "변경할 항목이 없습니다."
    ),
    STREAMER_INVALID_FIELD(
            HttpStatus.BAD_REQUEST,
            "프로필 필드 값이 올바르지 않습니다."
    ),
    STREAMER_INVALID_TAG(
            HttpStatus.BAD_REQUEST,
            "태그 카탈로그에 없는 태그이거나 중복 지정입니다."
    );

    private final HttpStatus status;
    private final String message;

    StreamerProfileErrorCode(HttpStatus status, String message) {
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

package com.example.laive.media.exception;

import org.springframework.http.HttpStatus;

/**
 * 클립·녹화 API가 외부에 공개하는 안정적인 오류 코드다.
 */
public enum MediaErrorCode {
    /** 없는 세션과 타인의 세션을 구분하지 않는다. 존재 여부를 노출하지 않기 위해서다. */
    RECORDING_SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "방송 세션을 찾을 수 없습니다."
    ),
    RECORDING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "녹화 기록이 없습니다. 녹화 시작을 먼저 보고하세요."
    ),
    HIGHLIGHT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "하이라이트를 찾을 수 없습니다."
    ),
    CLIP_NOT_PENDING(
            HttpStatus.CONFLICT,
            "클립을 기다리는 상태가 아닙니다."
    ),
    CLIP_INVALID_FILE(
            HttpStatus.BAD_REQUEST,
            "클립은 mp4, 썸네일은 jpeg여야 합니다."
    ),
    CLIP_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "클립 파일이 허용 크기를 넘었습니다."
    ),
    CLIP_STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "클립을 저장하지 못했습니다."
    );

    private final HttpStatus status;
    private final String message;

    MediaErrorCode(HttpStatus status, String message) {
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

package com.example.laive.broadcast.summary.exception;

import org.springframework.http.HttpStatus;

/**
 * 여론 요약 API에서 외부에 공개하는 안정적인 오류 코드다.
 */
public enum ChatSummaryErrorCode {
    SUMMARY_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "여론 요약 AI를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
    );

    private final HttpStatus status;
    private final String message;

    ChatSummaryErrorCode(HttpStatus status, String message) {
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

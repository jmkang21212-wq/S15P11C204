package com.example.laive.report.exception;

import org.springframework.http.HttpStatus;

public enum ReportErrorCode {
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "세션 리포트를 찾을 수 없습니다."),
    REPORT_GENERATING(HttpStatus.CONFLICT, "세션 리포트를 생성하고 있습니다."),
    REPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "세션 리포트 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    ReportErrorCode(HttpStatus status, String message) {
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

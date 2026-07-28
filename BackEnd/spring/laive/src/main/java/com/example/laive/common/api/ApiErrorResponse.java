package com.example.laive.common.api;

/**
 * 모든 실패 응답을 API 명세의 success/error 구조로 감싼다.
 */
public record ApiErrorResponse(boolean success, ErrorBody error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}


package com.example.laive.auth.api;

/**
 * 실패한 API 응답을 success/error 구조로 감싸는 공통 Envelope다.
 */
public record ApiErrorResponse(boolean success, ErrorBody error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorBody(code, message));
    }

    public record ErrorBody(String code, String message) {
    }
}

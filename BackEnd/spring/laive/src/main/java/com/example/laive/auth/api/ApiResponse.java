package com.example.laive.auth.api;

/**
 * 성공한 API 응답을 success/data 구조로 감싸는 공통 Envelope다.
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}

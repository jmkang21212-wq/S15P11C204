package com.example.laive.common.api;

/**
 * 모든 성공 응답을 API 명세의 success/data 구조로 감싼다.
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data);
    }
}


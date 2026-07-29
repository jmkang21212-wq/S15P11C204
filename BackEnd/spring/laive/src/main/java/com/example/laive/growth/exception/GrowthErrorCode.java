package com.example.laive.growth.exception;

import org.springframework.http.HttpStatus;

public enum GrowthErrorCode {
    GROWTH_GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "성장 목표를 찾을 수 없습니다."),
    INVALID_GROWTH_GOAL(HttpStatus.BAD_REQUEST, "성장 목표 값이 올바르지 않습니다."),
    GROWTH_GOAL_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 성장 목표를 변경할 수 없습니다."),
    INVALID_GROWTH_PERIOD(HttpStatus.BAD_REQUEST, "성장 리포트 조회 기간이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    GrowthErrorCode(HttpStatus status, String message) {
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

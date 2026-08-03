package com.example.laive.promise.exception;

import org.springframework.http.HttpStatus;

/**
 * 약속 관리 API가 외부에 공개하는 안정적인 오류 코드다.
 */
public enum PromiseErrorCode {
    PROMISE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "약속을 찾을 수 없습니다."
    ),
    PROMISE_NOT_DETECTED(
            HttpStatus.CONFLICT,
            "감지 상태(DETECTED)인 약속만 확정하거나 무시할 수 있습니다."
    ),
    PROMISE_NOT_CONFIRMED(
            HttpStatus.CONFLICT,
            "확정된 약속만 상태를 변경할 수 있습니다. 확정·무시는 전용 API를 사용하세요."
    ),
    PROMISE_INVALID_STATUS(
            HttpStatus.BAD_REQUEST,
            "변경할 수 있는 상태는 PENDING, ON_HOLD, COMPLETED, DISMISSED입니다. "
                    + "SCHEDULED는 일정 전환 API로만 도달합니다."
    ),
    PROMISE_NOT_SCHEDULABLE(
            HttpStatus.CONFLICT,
            "확정된 약속(PENDING·ON_HOLD)만 일정으로 전환할 수 있습니다."
    ),
    PROMISE_NOT_SCHEDULED(
            HttpStatus.CONFLICT,
            "일정이 잡힌 약속이 아닙니다."
    ),
    PROMISE_ALREADY_SCHEDULED(
            HttpStatus.CONFLICT,
            "이미 일정이 연결된 약속입니다. 기존 일정을 취소한 뒤 다시 잡으세요."
    ),
    PROMISE_PLATFORM_ACCOUNT_REQUIRED(
            HttpStatus.CONFLICT,
            "일정으로 전환하려면 연결된 플랫폼 계정이 필요합니다."
    ),
    PROMISE_INVALID_CONTENT(
            HttpStatus.BAD_REQUEST,
            "약속 내용은 1자 이상 500자 이하여야 합니다."
    ),
    PROMISE_EMPTY_PATCH(
            HttpStatus.BAD_REQUEST,
            "변경할 항목이 없습니다."
    );

    private final HttpStatus status;
    private final String message;

    PromiseErrorCode(HttpStatus status, String message) {
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

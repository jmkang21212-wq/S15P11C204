package com.example.laive.promise.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 약속 관리 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class PromiseException extends BusinessException {

    private PromiseException(PromiseErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    /** 없는 약속과 타인의 약속을 구분하지 않는다. 존재 여부를 노출하지 않기 위해서다. */
    public static PromiseException notFound() {
        return new PromiseException(PromiseErrorCode.PROMISE_NOT_FOUND);
    }

    public static PromiseException notDetected() {
        return new PromiseException(PromiseErrorCode.PROMISE_NOT_DETECTED);
    }

    public static PromiseException notConfirmed() {
        return new PromiseException(PromiseErrorCode.PROMISE_NOT_CONFIRMED);
    }

    public static PromiseException invalidStatus() {
        return new PromiseException(PromiseErrorCode.PROMISE_INVALID_STATUS);
    }

    public static PromiseException notSchedulable() {
        return new PromiseException(PromiseErrorCode.PROMISE_NOT_SCHEDULABLE);
    }

    public static PromiseException notScheduled() {
        return new PromiseException(PromiseErrorCode.PROMISE_NOT_SCHEDULED);
    }

    public static PromiseException alreadyScheduled() {
        return new PromiseException(PromiseErrorCode.PROMISE_ALREADY_SCHEDULED);
    }

    public static PromiseException platformAccountRequired() {
        return new PromiseException(PromiseErrorCode.PROMISE_PLATFORM_ACCOUNT_REQUIRED);
    }

    public static PromiseException invalidContent() {
        return new PromiseException(PromiseErrorCode.PROMISE_INVALID_CONTENT);
    }

    public static PromiseException emptyPatch() {
        return new PromiseException(PromiseErrorCode.PROMISE_EMPTY_PATCH);
    }
}

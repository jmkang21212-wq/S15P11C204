package com.example.laive.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 외부에 공개할 HTTP 상태와 안정적인 오류 코드를 보관하는 공통 비즈니스 예외다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}


package com.example.laive.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증 처리 중 발생한 HTTP 상태, 공개 오류 코드와 사용자 메시지를 함께 전달한다.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static AuthException invalidCredentials() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS",
                "인증 정보가 유효하지 않습니다."
        );
    }

    public static AuthException invalidRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "AUTH_INVALID_REQUEST", message);
    }

    public static AuthException platformConnectionFailed() {
        return new AuthException(
                HttpStatus.BAD_GATEWAY,
                "PLATFORM_CONNECTION_FAILED",
                "네이버 로그인 처리에 실패했습니다."
        );
    }
}

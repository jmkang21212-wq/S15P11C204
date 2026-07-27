package com.example.laive.platform.exception;

import com.example.laive.auth.api.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 플랫폼 연동 도메인 예외를 API 명세의 공통 오류 Envelope로 변환한다.
 */
@RestControllerAdvice
public class PlatformExceptionHandler {

    /** 예외에 담긴 HTTP 상태와 오류 코드를 그대로 응답한다. */
    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiErrorResponse> handlePlatformException(PlatformException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    /**
     * 콜백 실패는 오류 JSON 대신 클라이언트 복귀 주소로 error 코드를 붙여 돌려보낸다.
     * 사용자가 보고 있는 화면이 외부 브라우저이기 때문이다.
     */
    @ExceptionHandler(PlatformCallbackException.class)
    public ResponseEntity<Void> handlePlatformCallbackException(PlatformCallbackException exception) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(UriComponentsBuilder.fromUriString(exception.getReturnUri())
                        .queryParam("error", exception.getCode())
                        .build()
                        .toUri())
                .build();
    }
}

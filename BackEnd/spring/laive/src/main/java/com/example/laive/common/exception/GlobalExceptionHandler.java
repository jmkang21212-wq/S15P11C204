package com.example.laive.common.exception;

import com.example.laive.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 공통 비즈니스 예외와 요청 검증 오류를 동일한 오류 Envelope로 변환한다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            // multipart 파트 누락도 클라이언트 잘못이다. 빼먹으면 500으로 나가서
            // 호출자가 "서버 오류"로 읽고 원인을 못 찾는다.
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidationException(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("VALIDATION_ERROR", validationMessage(exception)));
    }

    /**
     * 없는 정적 리소스·매핑되지 않은 경로다.
     *
     * <p>아래 {@code Exception} 핸들러가 최하위로 잡으면 500이 나가는데, 이는 서버
     * 오류가 아니라 요청 잘못이다. 하이라이트 클립처럼 브라우저가 직접 받아 가는
     * 경로에서는 이 구분이 특히 중요하다 — {@code <video>}가 500을 받으면 재생 실패의
     * 원인이 서버 장애인지 파일 없음인지 알 수 없다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("처리되지 않은 서버 오류", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_SERVER_ERROR",
                        "서버 처리 중 오류가 발생했습니다."
                ));
    }

    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException
                && !validationException.getBindingResult().getFieldErrors().isEmpty()) {
            var error = validationException.getBindingResult().getFieldErrors().getFirst();
            return error.getField() + ": " + error.getDefaultMessage();
        }
        return "요청 값이 올바르지 않습니다.";
    }
}

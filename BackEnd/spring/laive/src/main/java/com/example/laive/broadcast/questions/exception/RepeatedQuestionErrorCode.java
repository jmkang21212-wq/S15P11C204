package com.example.laive.broadcast.questions.exception;

import org.springframework.http.HttpStatus;

/**
 * 반복 질문 API에서 외부에 공개하는 안정적인 오류 코드다.
 */
public enum RepeatedQuestionErrorCode {
    REPEATED_QUESTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "반복 질문을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String message;

    RepeatedQuestionErrorCode(HttpStatus status, String message) {
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

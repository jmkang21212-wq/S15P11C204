package com.example.laive.broadcast.questions.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 반복 질문 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class RepeatedQuestionException extends BusinessException {

    private RepeatedQuestionException(RepeatedQuestionErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    public static RepeatedQuestionException notFound() {
        return new RepeatedQuestionException(RepeatedQuestionErrorCode.REPEATED_QUESTION_NOT_FOUND);
    }
}

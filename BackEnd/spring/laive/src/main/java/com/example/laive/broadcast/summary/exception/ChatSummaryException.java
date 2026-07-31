package com.example.laive.broadcast.summary.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 여론 요약 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class ChatSummaryException extends BusinessException {

    private ChatSummaryException(ChatSummaryErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    public static ChatSummaryException unavailable() {
        return new ChatSummaryException(ChatSummaryErrorCode.SUMMARY_UNAVAILABLE);
    }
}

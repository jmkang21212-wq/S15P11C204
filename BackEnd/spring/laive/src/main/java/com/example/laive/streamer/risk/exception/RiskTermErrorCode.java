package com.example.laive.streamer.risk.exception;

import org.springframework.http.HttpStatus;

/**
 * 개인 사전 API가 외부에 공개하는 안정적인 오류 코드다.
 */
public enum RiskTermErrorCode {
    RISK_TERM_TOO_SHORT(
            HttpStatus.BAD_REQUEST,
            "두 글자 이상 등록해 주세요. 한 글자는 다른 단어에 묻혀 오탐이 납니다."
    ),
    RISK_TERM_TOO_LONG(
            HttpStatus.BAD_REQUEST,
            "40자 이하로 등록해 주세요."
    ),
    RISK_TERM_EMPTY(
            HttpStatus.BAD_REQUEST,
            "등록할 수 있는 글자가 없습니다. 한글·영문·숫자를 포함해 주세요."
    ),
    RISK_TERM_DUPLICATE(
            HttpStatus.CONFLICT,
            "이미 등록된 표현입니다."
    ),
    RISK_TERM_CONFLICTING_KIND(
            HttpStatus.CONFLICT,
            "같은 표현이 반대쪽 목록에 있습니다. 먼저 삭제해 주세요."
    ),
    RISK_TERM_LIMIT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "등록 개수 상한을 넘었습니다."
    ),
    RISK_TERM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "등록되지 않은 항목입니다."
    );

    private final HttpStatus status;
    private final String message;

    RiskTermErrorCode(HttpStatus status, String message) {
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

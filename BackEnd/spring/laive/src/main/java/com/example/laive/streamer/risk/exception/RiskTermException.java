package com.example.laive.streamer.risk.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 개인 사전 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class RiskTermException extends BusinessException {

    private RiskTermException(RiskTermErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    private RiskTermException(RiskTermErrorCode errorCode, String message) {
        super(errorCode.status(), errorCode.name(), message);
    }

    public static RiskTermException tooShort() {
        return new RiskTermException(RiskTermErrorCode.RISK_TERM_TOO_SHORT);
    }

    public static RiskTermException tooLong() {
        return new RiskTermException(RiskTermErrorCode.RISK_TERM_TOO_LONG);
    }

    public static RiskTermException empty() {
        return new RiskTermException(RiskTermErrorCode.RISK_TERM_EMPTY);
    }

    public static RiskTermException duplicate() {
        return new RiskTermException(RiskTermErrorCode.RISK_TERM_DUPLICATE);
    }

    public static RiskTermException conflictingKind() {
        return new RiskTermException(RiskTermErrorCode.RISK_TERM_CONFLICTING_KIND);
    }

    /** 상한이 종류마다 다를 수 있으므로 실제 값을 메시지에 실어 보낸다. */
    public static RiskTermException limitExceeded(int limit) {
        return new RiskTermException(
                RiskTermErrorCode.RISK_TERM_LIMIT_EXCEEDED,
                "종류당 %d개까지 등록할 수 있습니다.".formatted(limit)
        );
    }

    public static RiskTermException notFound() {
        return new RiskTermException(RiskTermErrorCode.RISK_TERM_NOT_FOUND);
    }
}

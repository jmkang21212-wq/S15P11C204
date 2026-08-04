package com.example.laive.portfolio.exception;

import com.example.laive.common.exception.BusinessException;

/**
 * 포트폴리오 도메인의 오류 코드를 공통 BusinessException으로 전달한다.
 */
public class PortfolioException extends BusinessException {

    private PortfolioException(PortfolioErrorCode errorCode) {
        super(errorCode.status(), errorCode.name(), errorCode.message());
    }

    private PortfolioException(PortfolioErrorCode errorCode, String message) {
        super(errorCode.status(), errorCode.name(), message);
    }

    /** 미존재와 비공개를 구분하지 않는다. 비공개 포트폴리오의 존재를 노출하지 않기 위해서다. */
    public static PortfolioException notFound() {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_FOUND);
    }

    public static PortfolioException notEligible() {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_ELIGIBLE);
    }

    public static PortfolioException notCreated() {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_NOT_CREATED);
    }

    public static PortfolioException alreadyExists() {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_ALREADY_EXISTS);
    }

    public static PortfolioException emptyPatch() {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_EMPTY_PATCH);
    }

    /** 어떤 필드가 왜 거절됐는지 메시지로 전달한다. 코드는 하나로 안정적으로 유지한다. */
    public static PortfolioException invalidField(String message) {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_INVALID_FIELD, message);
    }

    public static PortfolioException highlightInvalid() {
        return new PortfolioException(PortfolioErrorCode.PORTFOLIO_HIGHLIGHT_INVALID);
    }
}

package com.example.laive.portfolio.exception;

import org.springframework.http.HttpStatus;

/**
 * 포트폴리오 API가 외부에 공개하는 안정적인 오류 코드다.
 *
 * <p>미생성 404를 두 코드로 나눈 이유: 프론트가 "첫 방송 전" 안내 화면과
 * "생성 가능 + 생성하기 버튼" 화면을 구분해 그려야 하기 때문이다.
 */
public enum PortfolioErrorCode {
    PORTFOLIO_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "포트폴리오가 없거나 비공개입니다."
    ),
    PORTFOLIO_NOT_ELIGIBLE(
            HttpStatus.NOT_FOUND,
            "종료된 방송 세션이 1개 이상 있어야 포트폴리오를 만들 수 있습니다."
    ),
    PORTFOLIO_NOT_CREATED(
            HttpStatus.NOT_FOUND,
            "아직 포트폴리오를 만들지 않았습니다. 생성 API로 만들 수 있습니다."
    ),
    PORTFOLIO_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "포트폴리오가 이미 있습니다. 수정 API를 사용하세요."
    ),
    PORTFOLIO_EMPTY_PATCH(
            HttpStatus.BAD_REQUEST,
            "변경할 항목이 없습니다."
    ),
    PORTFOLIO_INVALID_FIELD(
            HttpStatus.BAD_REQUEST,
            "포트폴리오 필드 값이 올바르지 않습니다."
    ),
    PORTFOLIO_HIGHLIGHT_INVALID(
            HttpStatus.BAD_REQUEST,
            "본인 소유가 아니거나 잘못 지정된 하이라이트입니다."
    );

    private final HttpStatus status;
    private final String message;

    PortfolioErrorCode(HttpStatus status, String message) {
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

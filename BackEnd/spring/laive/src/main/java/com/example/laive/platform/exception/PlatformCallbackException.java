package com.example.laive.platform.exception;

import lombok.Getter;

/**
 * 콜백 처리 중 실패했지만 복귀 주소는 이미 확보한 상황을 나타낸다.
 *
 * 콜백은 외부 브라우저가 보는 화면이라 오류 JSON을 그대로 노출하는 대신
 * 복귀 주소로 error 코드를 붙여 리다이렉트한다.
 */
@Getter
public class PlatformCallbackException extends RuntimeException {

    private final String returnUri;
    private final String code;

    public PlatformCallbackException(String returnUri, PlatformException cause) {
        super(cause.getMessage(), cause);
        this.returnUri = returnUri;
        this.code = cause.getCode();
    }
}

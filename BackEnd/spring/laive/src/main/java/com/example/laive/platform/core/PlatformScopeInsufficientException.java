package com.example.laive.platform.core;

/**
 * 저장 credential이 유효하지만 요청에 필요한 scope가 없음을 나타낸다.
 * 토큰 갱신으로는 해소되지 않고 사용자가 다시 연동해 새 권한을 승인해야 한다.
 */
public class PlatformScopeInsufficientException extends RuntimeException {

    public PlatformScopeInsufficientException(String message, Throwable cause) {
        super(message, cause);
    }
}

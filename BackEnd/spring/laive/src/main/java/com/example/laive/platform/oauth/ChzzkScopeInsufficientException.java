package com.example.laive.platform.oauth;

/**
 * 치지직이 권한 부족(403)으로 요청을 거부했음을 알린다.
 * 방송 설정 권한이 앱에 추가되기 전에 연동한 계정의 토큰에는 해당 scope가 없어
 * 발생한다. 토큰 자체는 유효하므로(채팅 수집 등은 계속 동작) 재발급이 아니라
 * 재연동으로만 해소된다 — 사용자 응답 매핑은 서비스 계층에서 결정한다.
 */
public class ChzzkScopeInsufficientException extends RuntimeException {

    public ChzzkScopeInsufficientException(String message) {
        super(message);
    }
}

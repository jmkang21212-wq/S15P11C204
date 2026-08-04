package com.example.laive.platform.oauth;

/**
 * 치지직이 토큰을 거부(401 INVALID_TOKEN)했음을 알린다.
 * 같은 401이라도 최초 연동 중이면 연동 실패, 이미 연동된 계정이면 재연동 필요로
 * 의미가 달라지므로 사용자 응답 매핑은 서비스 계층에서 결정한다.
 */
public class ChzzkTokenRejectedException extends RuntimeException {

    public ChzzkTokenRejectedException(String message) {
        super(message);
    }
}

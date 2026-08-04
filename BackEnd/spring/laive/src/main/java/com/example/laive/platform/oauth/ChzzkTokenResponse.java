package com.example.laive.platform.oauth;

/**
 * 치지직 토큰 발급·갱신 응답이다.
 * expiresIn은 Access Token의 남은 수명(초)이며 실제 응답은 숫자 86400(1일)이다.
 * scope는 공백으로 구분된 한글 표시명 문자열이라 구분자를 신뢰할 수 없으므로
 * 파싱하지 않고 원문 그대로 보관한다.
 */
public record ChzzkTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String scope
) {
}

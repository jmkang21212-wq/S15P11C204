package com.example.laive.platform.api;

/**
 * 클라이언트가 외부 브라우저로 열어야 할 플랫폼 인증 URL이다.
 *
 * 소셜 로그인과 달리 302로 보내지 않는다. 플랫폼 연동은 로그인한 회원에 붙이는
 * 동작이라 Access Token이 필요한데 브라우저 리다이렉트에는 헤더를 실을 수 없다.
 */
public record PlatformAuthorizeResponse(String authorizeUrl) {
}

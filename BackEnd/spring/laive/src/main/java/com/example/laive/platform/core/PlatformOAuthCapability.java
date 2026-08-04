package com.example.laive.platform.core;

/** OAuth 연동을 지원하는 플랫폼 Adapter의 선택 기능이다. */
public interface PlatformOAuthCapability {

    String buildAuthorizationUri(String state);

    PlatformOAuthTokens exchange(String code, String state);

    PlatformOAuthTokens refresh(String refreshToken);

    PlatformIdentity fetchIdentity(String accessToken);

    void revoke(String accessToken);
}

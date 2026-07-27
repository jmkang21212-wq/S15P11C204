package com.example.laive.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.yaml의 방송 플랫폼 연동(치지직) 설정을 타입 안전하게 바인딩한다.
 */
@ConfigurationProperties(prefix = "laive.platform")
public record PlatformProperties(
        Chzzk chzzk,
        String tokenEncryptionKey,
        Duration linkStateTtl,
        Duration accessTokenRefreshThreshold
) {
    public PlatformProperties {
        linkStateTtl = linkStateTtl == null ? Duration.ofMinutes(5) : linkStateTtl;
        accessTokenRefreshThreshold =
                accessTokenRefreshThreshold == null ? Duration.ofMinutes(5) : accessTokenRefreshThreshold;
    }

    /**
     * 치지직 인증 화면은 OPEN API와 다른 도메인을 사용하므로 URI를 따로 설정한다.
     */
    public record Chzzk(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizationUri,
            String tokenUri,
            String revokeUri,
            String userMeUri
    ) {
    }
}

package com.example.laive.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/**
 * application.yaml의 방송 플랫폼 연동(치지직) 설정을 타입 안전하게 바인딩한다.
 */
@ConfigurationProperties(prefix = "laive.platform")
public record PlatformProperties(
        Chzzk chzzk,
        YouTube youtube,
        String tokenEncryptionKey,
        Duration linkStateTtl,
        Duration accessTokenRefreshThreshold
) {
    @ConstructorBinding
    public PlatformProperties {
        linkStateTtl = linkStateTtl == null ? Duration.ofMinutes(5) : linkStateTtl;
        accessTokenRefreshThreshold =
                accessTokenRefreshThreshold == null ? Duration.ofMinutes(5) : accessTokenRefreshThreshold;
    }

    /** 기존 테스트와 단일 CHZZK 설정의 소스 호환성을 유지한다. */
    public PlatformProperties(
            Chzzk chzzk,
            String tokenEncryptionKey,
            Duration linkStateTtl,
            Duration accessTokenRefreshThreshold
    ) {
        this(
                chzzk,
                new YouTube("", "", "", "https://accounts.google.com/o/oauth2/v2/auth",
                        "https://oauth2.googleapis.com/token", "https://oauth2.googleapis.com/revoke",
                        "https://www.googleapis.com"),
                tokenEncryptionKey,
                linkStateTtl,
                accessTokenRefreshThreshold
        );
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

    /** YouTube OAuth 및 Data/Live Streaming API 접속 정보다. */
    public record YouTube(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizationUri,
            String tokenUri,
            String revokeUri,
            String apiBaseUrl
    ) {
    }
}

package com.example.laive.auth.oauth;

import com.example.laive.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NaverOAuthClientTest {

    @Test
    void encodesRedirectUriWhenBuildingAuthorizationUri() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Naver(
                        "client-id",
                        "client-secret",
                        "http://localhost:8080/api/v1/auth/oauth/naver/callback",
                        "https://nid.naver.com/oauth2.0/authorize",
                        "https://nid.naver.com/oauth2.0/token",
                        "https://openapi.naver.com/v1/nid/me"
                ),
                new AuthProperties.Jwt(
                        "01234567890123456789012345678901",
                        Duration.ofMinutes(30),
                        "laive"
                ),
                new AuthProperties.RefreshCookie(
                        "laive_refresh",
                        Duration.ofDays(14),
                        false
                ),
                List.of("http://localhost:3000/oauth/callback"),
                Duration.ofMinutes(5)
        );
        NaverOAuthClient client = new NaverOAuthClient(RestClient.builder(), properties);

        String authorizationUri = client.buildAuthorizationUri("state-value");

        assertThat(authorizationUri)
                .contains("client_id=client-id")
                .contains(
                        "redirect_uri=http%3A%2F%2Flocalhost%3A8080"
                                + "%2Fapi%2Fv1%2Fauth%2Foauth%2Fnaver%2Fcallback"
                )
                .contains("state=state-value");
    }
}

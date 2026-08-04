package com.example.laive.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * application.yaml의 OAuth, JWT, Refresh 쿠키 관련 설정을 타입 안전하게 바인딩한다.
 */
@ConfigurationProperties(prefix = "laive.auth")
public record AuthProperties(
        Naver naver,
        Jwt jwt,
        RefreshCookie refreshCookie,
        List<String> allowedReturnUris,
        Duration oauthStateTtl
) {
    public AuthProperties {
        allowedReturnUris = allowedReturnUris == null ? List.of() : List.copyOf(allowedReturnUris);
        oauthStateTtl = oauthStateTtl == null ? Duration.ofMinutes(5) : oauthStateTtl;
    }

    public record Naver(
            String clientId,
            String clientSecret,
            String redirectUri,
            String authorizationUri,
            String tokenUri,
            String userInfoUri
    ) {
    }

    public record Jwt(String secret, Duration accessTokenTtl, String issuer) {
        public Jwt {
            accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(30) : accessTokenTtl;
            issuer = issuer == null || issuer.isBlank() ? "laive" : issuer;
        }
    }

    public record RefreshCookie(String name, Duration ttl, boolean secure, Duration rotationGrace) {
        public RefreshCookie {
            name = name == null || name.isBlank() ? "laive_refresh" : name;
            ttl = ttl == null ? Duration.ofDays(14) : ttl;
            rotationGrace = rotationGrace == null ? Duration.ofSeconds(60) : rotationGrace;
        }
    }

    public Set<String> allowedOrigins() {
        // returnUri에 path가 있어도 CORS 비교에는 scheme/host/port로 만든 Origin만 사용한다.
        Set<String> origins = new LinkedHashSet<>();
        for (String returnUri : allowedReturnUris) {
            try {
                URI uri = URI.create(returnUri);
                if (uri.getScheme() != null && uri.getHost() != null) {
                    int port = uri.getPort();
                    origins.add(uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port));
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid entries are rejected when used as a return URI.
            }
        }
        return origins;
    }
}

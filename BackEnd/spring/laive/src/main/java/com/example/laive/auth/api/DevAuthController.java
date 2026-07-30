package com.example.laive.auth.api;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.oauth.NaverProfile;
import com.example.laive.auth.service.AuthService;
import com.example.laive.auth.service.RefreshTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발 전용 로그인 API. 네이버 인증 없이 고정 개발 계정으로
 * Refresh Token 쿠키를 발급해 이후 플로우(refresh, 보호 API)를 실제 경로 그대로 태운다.
 * laive.auth.dev-login-enabled=true(.env의 DEV_LOGIN_ENABLED)일 때만 빈이 등록되므로
 * 기본 설정의 배포 환경에는 이 엔드포인트 자체가 존재하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/auth/dev")
@ConditionalOnProperty(prefix = "laive.auth", name = "dev-login-enabled", havingValue = "true")
public class DevAuthController {

    /** 실제 네이버 provider ID와 충돌하지 않는 고정 식별자. 첫 로그인 때 계정이 생성된다. */
    private static final NaverProfile DEV_PROFILE =
            new NaverProfile("dev-local-account", null, "개발용 계정", null, null);

    private final AuthService authService;
    private final AuthProperties properties;

    public DevAuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 고정 개발 계정으로 로그인하고 Refresh Token 쿠키를 심는다.
     * Access Token은 실제 플로우와 동일하게 POST /api/v1/auth/refresh로 받아간다.
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login() {
        AuthService.LoginCompletion login = authService.completeNaverLogin(DEV_PROFILE);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(login.refreshToken()).toString())
                .build();
    }

    private ResponseCookie refreshCookie(RefreshTokenService.IssuedRefreshToken token) {
        // AuthController의 콜백과 동일한 속성으로 쿠키를 만든다.
        return ResponseCookie.from(properties.refreshCookie().name(), token.value())
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(properties.refreshCookie().ttl())
                .build();
    }
}

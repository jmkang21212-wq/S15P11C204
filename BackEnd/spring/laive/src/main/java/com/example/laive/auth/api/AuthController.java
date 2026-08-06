package com.example.laive.auth.api;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.exception.AuthException;
import com.example.laive.auth.oauth.NaverOAuthClient;
import com.example.laive.auth.oauth.NaverProfile;
import com.example.laive.auth.service.AuthService;
import com.example.laive.auth.service.OAuthStateService;
import com.example.laive.auth.service.RefreshTokenService;
import com.example.laive.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 네이버 OAuth 로그인 시작/콜백과 Refresh Token 갱신·로그아웃 API를 제공한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final OAuthStateService stateService;
    private final NaverOAuthClient naverOAuthClient;
    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(
            OAuthStateService stateService,
            NaverOAuthClient naverOAuthClient,
            AuthService authService,
            AuthProperties properties
    ) {
        this.stateService = stateService;
        this.naverOAuthClient = naverOAuthClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * 클라이언트의 복귀 주소를 검증하고 OAuth state를 만든 뒤 네이버 인증 화면으로 이동시킨다.
     */
    @GetMapping("/oauth/{provider}/authorize")
    public ResponseEntity<Void> authorize(
            @PathVariable String provider,
            @RequestParam String returnUri
    ) {
        requireNaver(provider);
        String state = stateService.create(returnUri);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(naverOAuthClient.buildAuthorizationUri(state)))
                .build();
    }

    /**
     * 네이버가 전달한 code/state를 검증하고 회원가입 또는 로그인을 완료한다.
     * 토큰은 URL에 노출하지 않고 Refresh Token만 HttpOnly 쿠키로 전달한다.
     */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam String state,
            @RequestParam(required = false) String error
    ) {
        requireNaver(provider);
        String returnUri = stateService.consume(state);
        if (error != null || code == null || code.isBlank()) {
            throw AuthException.invalidCredentials();
        }

        NaverProfile profile = naverOAuthClient.fetchProfile(code, state);
        AuthService.LoginCompletion login = authService.completeNaverLogin(profile);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(login.refreshToken()).toString())
                .location(URI.create(returnUri))
                .build();
    }

    /**
     * 유효한 Refresh Token을 새 토큰으로 회전하고 30분짜리 Access Token을 응답한다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @CookieValue(name = "${laive.auth.refresh-cookie.name:laive_refresh}", required = false)
            String refreshToken
    ) {
        AuthService.RefreshedLogin refreshed = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshed.refreshToken()).toString())
                .body(ApiResponse.success(refreshed.response()));
    }

    /**
     * 현재 Refresh Token을 폐기하고 브라우저의 Refresh Token 쿠키도 즉시 만료시킨다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${laive.auth.refresh-cookie.name:laive_refresh}", required = false)
            String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    private void requireNaver(String provider) {
        if (!"naver".equalsIgnoreCase(provider)) {
            throw AuthException.invalidRequest("지원하지 않는 OAuth provider입니다.");
        }
    }

    private ResponseCookie refreshCookie(RefreshTokenService.IssuedRefreshToken token) {
        // JavaScript에서는 읽을 수 없고 인증 API로만 전송되는 쿠키를 만든다.
        return ResponseCookie.from(properties.refreshCookie().name(), token.value())
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                .sameSite(sameSite())
                .path("/api/v1/auth")
                .maxAge(properties.refreshCookie().ttl())
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        // 동일한 이름과 경로에 Max-Age=0 쿠키를 내려 브라우저 저장값을 삭제한다.
        return ResponseCookie.from(properties.refreshCookie().name(), "")
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                .sameSite(sameSite())
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    private String sameSite() {
        // 설치형 Electron 앱은 렌더러가 file:// 오리진이라 API 호출이 크로스 사이트다.
        // Lax면 브라우저가 refresh 요청에 쿠키를 싣지 않아 로그인이 마지막 단계(401)에서
        // 끊긴다. None은 Secure가 필수라 secure 플래그와 묶는다 — 배포(HTTPS)는 None,
        // 로컬 개발(http, 같은 사이트라 Lax로 충분)은 기존 동작을 유지한다.
        return properties.refreshCookie().secure() ? "None" : "Lax";
    }
}

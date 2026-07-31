package com.example.laive.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * 인증 API 공개 범위, JWT 검증, CORS와 인증 실패 응답 형식을 설정한다.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthSecurityConfig {

    /**
     * OAuth/토큰 API만 공개하고 그 외 요청에는 Bearer JWT 인증을 요구한다.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/oauth/**",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                // 개발용 로그인. dev-login-enabled=false면 빈이 없어 404다.
                                "/api/v1/auth/dev/**",
                                // 플랫폼 콜백은 브라우저 리다이렉트라 인증 헤더가 없다.
                                // 연동 주체는 state로만 판별한다.
                                "/api/v1/platform-accounts/*/callback",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "AUTH_INVALID_CREDENTIALS",
                                        "인증 정보가 유효하지 않습니다."
                                )))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "AUTH_INVALID_CREDENTIALS",
                                        "인증 정보가 유효하지 않습니다."
                                ))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(
                                        response,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "AUTH_ACCESS_DENIED",
                                        "접근 권한이 없습니다."
                                )));
        return http.build();
    }

    /** Access Token 서명에 사용하는 HS256 JWT Encoder를 구성한다. */
    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return NimbusJwtEncoder.withSecretKey(jwtSecret(properties)).build();
    }

    /** 서명, issuer, 만료 시각을 검증하는 JWT Decoder를 구성한다. */
    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecret(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validator =
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer());
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** 토큰 발급 시각을 테스트에서 고정할 수 있도록 Clock을 의존성으로 제공한다. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** 네이버 OAuth HTTP 호출에 사용할 Spring RestClient Builder를 제공한다. */
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 허용된 returnUri의 Origin만 credential 포함 API 요청을 보낼 수 있게 한다.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.copyOf(properties.allowedOrigins()));
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private SecretKey jwtSecret(AuthProperties properties) {
        // HS256의 최소 보안 강도를 위해 32바이트 미만의 키로는 애플리케이션을 시작하지 않는다.
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private void writeSecurityError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws java.io.IOException {
        // Spring Security 필터 단계의 오류도 컨트롤러 오류와 동일한 Envelope로 반환한다.
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"error\":{\"code\":\""
                        + code
                        + "\",\"message\":\""
                        + message
                        + "\"}}"
        );
    }
}

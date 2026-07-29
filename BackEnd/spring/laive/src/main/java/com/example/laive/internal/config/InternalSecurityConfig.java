package com.example.laive.internal.config;

import com.example.laive.internal.security.ServiceTokenAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * `/internal/**`를 사용자 JWT가 아닌 서비스 토큰으로 인증하는 별도 필터 체인이다.
 *
 * <p>수집기와 AI 서버는 스트리머 계정이 없어 JWT를 발급받을 수 없다. 기본 체인의
 * {@code anyRequest().authenticated()}는 Bearer를 JWT로 해석하므로 내부 호출이
 * 통과할 수 없고, 그렇다고 permitAll로 열면 인증 없는 쓰기 Endpoint가 된다.
 * 경로를 먼저 가로채는 체인을 하나 더 두어 인증 방식만 갈아 끼운다.
 */
@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(InternalSecurityConfig.class);

    /**
     * AuthSecurityConfig의 체인은 securityMatcher가 없는 Catch-all이라 마지막에 와야 한다.
     * 이 체인이 먼저 평가되도록 명시적으로 앞 순서를 준다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            InternalApiProperties properties
    ) throws Exception {
        if (!properties.hasServiceToken()) {
            // 설정 누락으로 내부 API가 열리는 것보다 전부 막히는 편이 안전하다.
            // 기동은 시켜야 로컬에서 나머지 API를 개발할 수 있으므로 경고만 남긴다.
            log.warn("laive.internal.service-token이 비어 있어 /internal/** 요청을 모두 거부합니다. "
                    + "수집기와 AI 서버 연동에는 INTERNAL_SERVICE_TOKEN 설정이 필요합니다.");
        }

        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                // 내부 호출은 브라우저에서 오지 않는다. CORS를 열 이유가 없다.
                .cors(cors -> cors.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasRole("INTERNAL"))
                .addFilterBefore(
                        new ServiceTokenAuthenticationFilter(properties),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(
                                        response,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "INTERNAL_UNAUTHORIZED",
                                        "내부 서비스 토큰이 유효하지 않습니다."
                                ))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        response,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "INTERNAL_FORBIDDEN",
                                        "내부 API 접근 권한이 없습니다."
                                )));
        return http.build();
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws java.io.IOException {
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

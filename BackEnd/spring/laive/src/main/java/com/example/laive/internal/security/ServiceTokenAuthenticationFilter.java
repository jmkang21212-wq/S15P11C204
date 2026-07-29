package com.example.laive.internal.security;

import com.example.laive.internal.config.InternalApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * `Authorization: Bearer {serviceToken}`으로 내부 호출자를 인증한다(명세 13.1).
 *
 * <p>사용자 JWT와 달리 서비스 토큰은 회전 주기가 긴 고정 문자열이므로 이 필터는
 * 서명 검증이 아니라 단순 일치 비교를 한다. 대신 비교를 상수 시간으로 수행해
 * 응답 시간 차이로 토큰을 한 바이트씩 알아내는 것을 막는다.
 */
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final InternalApiProperties properties;

    public ServiceTokenAuthenticationFilter(InternalApiProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // 토큰이 설정되지 않은 환경에서는 인증을 세우지 않는다. 뒤의
        // authorizeHttpRequests가 401로 막으므로 설정 누락이 내부 API를
        // 익명에게 여는 방향으로 실패하지 않는다.
        if (properties.hasServiceToken()) {
            String presented = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
            if (presented != null && matches(presented, properties.serviceToken())) {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        "internal-service",
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_INTERNAL")
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String bearerToken(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    private boolean matches(String presented, String expected) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}

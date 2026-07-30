package com.example.laive.auth.service;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.domain.RefreshTokenRecord;
import com.example.laive.auth.domain.Streamer;
import com.example.laive.auth.exception.AuthException;
import com.example.laive.auth.persistence.AuthMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 장기 로그인 세션용 Refresh Token의 생성·회전·폐기와 해시 저장을 담당한다.
 */
@Service
public class RefreshTokenService {

    private final AuthMapper authMapper;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(AuthMapper authMapper, AuthProperties properties, Clock clock) {
        this.authMapper = authMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 256비트 난수 토큰을 만들고 원문 대신 SHA-256 해시만 DB에 저장한다.
     */
    @Transactional
    public IssuedRefreshToken issue(long streamerId) {
        String rawToken = generateToken();
        Instant expiresAt = clock.instant().plus(properties.refreshCookie().ttl());
        authMapper.insertRefreshToken(streamerId, hash(rawToken), expiresAt);
        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /**
     * 기존 토큰 행을 잠근 뒤 폐기하고 새 토큰으로 교체해 재사용 공격을 차단한다.
     * 이미 폐기된 토큰이라도 폐기 후 유예 기간 안이면 회전을 허용한다 —
     * 여러 창(메인·미니 모드)이 같은 쿠키로 거의 동시에 refresh를 보내는 경우를 위해서다.
     */
    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AuthException.invalidCredentials();
        }

        Instant now = clock.instant();
        RefreshTokenRecord current = authMapper.findRefreshTokenForUpdate(hash(rawToken))
                .filter(token -> token.isUsableAt(now, properties.refreshCookie().rotationGrace()))
                .orElseThrow(AuthException::invalidCredentials);

        // 유예 기간 내 재사용이면 이미 폐기된 상태이므로 다시 폐기하지 않는다.
        if (current.revokedAt() == null && authMapper.revokeRefreshToken(current.id(), now) != 1) {
            throw AuthException.invalidCredentials();
        }

        Streamer streamer = authMapper.findStreamerById(current.streamerId())
                .orElseThrow(AuthException::invalidCredentials);
        IssuedRefreshToken next = issue(current.streamerId());
        return new RotatedRefreshToken(next, streamer);
    }

    /**
     * 로그아웃 대상 Refresh Token을 폐기한다. 빈 토큰은 이미 로그아웃된 상태로 취급한다.
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        authMapper.revokeRefreshTokenByHash(hash(rawToken), clock.instant());
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String generateToken() {
        // 쿠키와 URL-safe 전송을 위해 패딩 없는 Base64 URL 형식으로 인코딩한다.
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record RotatedRefreshToken(IssuedRefreshToken token, Streamer streamer) {
    }
}

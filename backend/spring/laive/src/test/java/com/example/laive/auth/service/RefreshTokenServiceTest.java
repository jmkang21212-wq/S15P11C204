package com.example.laive.auth.service;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.domain.RefreshTokenRecord;
import com.example.laive.auth.domain.Streamer;
import com.example.laive.auth.exception.AuthException;
import com.example.laive.auth.persistence.AuthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    private AuthMapper authMapper;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        authMapper = mock(AuthMapper.class);
        service = new RefreshTokenService(
                authMapper,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void storesOnlyHashWhenIssuingRefreshToken() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(101L);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(authMapper).insertRefreshToken(
                org.mockito.ArgumentMatchers.eq(101L),
                hash.capture(),
                org.mockito.ArgumentMatchers.eq(NOW.plus(Duration.ofDays(14)))
        );
        assertThat(issued.value()).isNotBlank();
        assertThat(hash.getValue()).hasSize(64).isNotEqualTo(issued.value());
        assertThat(hash.getValue()).isEqualTo(RefreshTokenService.hash(issued.value()));
    }

    @Test
    void rotatesUsableRefreshTokenAndReturnsStreamer() {
        String currentRawToken = "current-token";
        RefreshTokenRecord current =
                new RefreshTokenRecord(7L, 101L, NOW.plusSeconds(60), null);
        Streamer streamer = new Streamer(101L, null, "네이버 사용자", null);
        when(authMapper.findRefreshTokenForUpdate(RefreshTokenService.hash(currentRawToken)))
                .thenReturn(Optional.of(current));
        when(authMapper.revokeRefreshToken(7L, NOW)).thenReturn(1);
        when(authMapper.findStreamerById(101L)).thenReturn(Optional.of(streamer));

        RefreshTokenService.RotatedRefreshToken rotated = service.rotate(currentRawToken);

        assertThat(rotated.streamer()).isEqualTo(streamer);
        assertThat(rotated.token().value()).isNotEqualTo(currentRawToken);
        verify(authMapper).insertRefreshToken(
                org.mockito.ArgumentMatchers.eq(101L),
                anyString(),
                org.mockito.ArgumentMatchers.eq(NOW.plus(Duration.ofDays(14)))
        );
    }

    @Test
    void rejectsExpiredRefreshToken() {
        when(authMapper.findRefreshTokenForUpdate(anyString())).thenReturn(Optional.of(
                new RefreshTokenRecord(7L, 101L, NOW.minusSeconds(1), null)
        ));

        assertThatThrownBy(() -> service.rotate("expired-token"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    private AuthProperties properties() {
        return new AuthProperties(
                new AuthProperties.Naver("", "", "", "", "", ""),
                new AuthProperties.Jwt("01234567890123456789012345678901", Duration.ofMinutes(30), "laive"),
                new AuthProperties.RefreshCookie("laive_refresh", Duration.ofDays(14), false),
                List.of(),
                Duration.ofMinutes(5)
        );
    }
}

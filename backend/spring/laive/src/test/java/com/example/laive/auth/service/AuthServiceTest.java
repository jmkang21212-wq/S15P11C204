package com.example.laive.auth.service;

import com.example.laive.auth.api.AuthTokenResponse;
import com.example.laive.auth.domain.Streamer;
import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.oauth.NaverProfile;
import com.example.laive.auth.persistence.AuthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthMapper authMapper;
    private RefreshTokenService refreshTokenService;
    private JwtTokenService jwtTokenService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        authMapper = mock(AuthMapper.class);
        refreshTokenService = mock(RefreshTokenService.class);
        jwtTokenService = mock(JwtTokenService.class);
        service = new AuthService(authMapper, refreshTokenService, jwtTokenService);
        when(refreshTokenService.issue(anyLong())).thenReturn(
                new RefreshTokenService.IssuedRefreshToken(
                        "refresh-token",
                        Instant.parse("2026-08-10T00:00:00Z")
                )
        );
    }

    @Test
    void logsInExistingSocialAccountWithoutCreatingStreamer() {
        Streamer existing = new Streamer(101L, "user@example.com", "기존 사용자", null);
        NaverProfile profile =
                new NaverProfile("naver-id", "user@example.com", "새 닉네임", null, null);
        when(authMapper.findStreamerBySocialAccount("NAVER", "naver-id"))
                .thenReturn(Optional.of(existing));

        AuthService.LoginCompletion result = service.completeNaverLogin(profile);

        assertThat(result.streamer()).isEqualTo(existing);
        verify(authMapper, never()).insertStreamer(any());
        verify(authMapper).updateProviderEmail("NAVER", "naver-id", "user@example.com");
    }

    @Test
    void createsSocialOnlyStreamerAndKeepsDuplicateEmailOnlyOnSocialAccount() {
        NaverProfile profile =
                new NaverProfile("naver-id", "used@example.com", null, "새 사용자", null);
        when(authMapper.findStreamerBySocialAccount("NAVER", "naver-id"))
                .thenReturn(Optional.empty());
        when(authMapper.existsStreamerByEmail("used@example.com")).thenReturn(true);
        doAnswer(invocation -> {
            StreamerInsert insert = invocation.getArgument(0);
            insert.setId(202L);
            return null;
        }).when(authMapper).insertStreamer(any(StreamerInsert.class));

        AuthService.LoginCompletion result = service.completeNaverLogin(profile);

        assertThat(result.streamer().streamerId()).isEqualTo(202L);
        assertThat(result.streamer().email()).isNull();
        assertThat(result.streamer().nickname()).isEqualTo("새 사용자");
        verify(authMapper).insertSocialAccount(
                202L,
                "NAVER",
                "naver-id",
                "used@example.com"
        );
    }

    @Test
    void refreshReturnsAccessTokenAndRotatedCookieToken() {
        Streamer streamer = new Streamer(101L, null, "사용자", null);
        RefreshTokenService.IssuedRefreshToken next =
                new RefreshTokenService.IssuedRefreshToken(
                        "next-refresh",
                        Instant.parse("2026-08-10T00:00:00Z")
                );
        when(refreshTokenService.rotate("current-refresh"))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(next, streamer));
        when(jwtTokenService.issue(101L)).thenReturn("access-token");
        when(jwtTokenService.expiresInSeconds()).thenReturn(1800L);

        AuthService.RefreshedLogin refreshed = service.refresh("current-refresh");

        assertThat(refreshed.refreshToken()).isEqualTo(next);
        assertThat(refreshed.response()).isEqualTo(
                new AuthTokenResponse("access-token", 1800L, streamer)
        );
    }
}

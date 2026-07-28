package com.example.laive.platform.service;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.oauth.ChzzkTokenRejectedException;
import com.example.laive.platform.oauth.ChzzkTokenResponse;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.platform.service.PlatformTokenRefresher.TokenResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformTokenRefresherTest {

    private static final long STREAMER_ID = 7L;
    private static final long ACCOUNT_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final String KEY = "dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=";

    private PlatformAccountMapper mapper;
    private ChzzkOAuthClient chzzkOAuthClient;
    private TokenCipher tokenCipher;
    private PlatformTokenRefresher refresher;

    @BeforeEach
    void setUp() {
        mapper = mock(PlatformAccountMapper.class);
        chzzkOAuthClient = mock(ChzzkOAuthClient.class);
        tokenCipher = new TokenCipher(properties());
        refresher = new PlatformTokenRefresher(
                mapper,
                chzzkOAuthClient,
                tokenCipher,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void reusesStoredTokenWhileItIsStillValid() {
        givenAccount(NOW.plus(Duration.ofHours(12)), NOW.plus(Duration.ofDays(20)), "CONNECTED");

        TokenResolution resolution = refresher.resolve(STREAMER_ID, ACCOUNT_ID);

        assertThat(resolution.reauthRequired()).isFalse();
        assertThat(resolution.accessToken()).isEqualTo("stored-access-token");
        // 갱신하면 기존 Access Token이 즉시 죽으므로 필요할 때만 호출해야 한다.
        verifyNoInteractions(chzzkOAuthClient);
    }

    @Test
    void refreshesAndStoresRotatedPairWhenExpiryIsNear() {
        givenAccount(NOW.plus(Duration.ofMinutes(1)), NOW.plus(Duration.ofDays(20)), "CONNECTED");
        when(chzzkOAuthClient.refreshToken("stored-refresh-token")).thenReturn(
                new ChzzkTokenResponse("new-access-token", "new-refresh-token", "Bearer", 86400L, "유저 조회")
        );

        TokenResolution resolution = refresher.resolve(STREAMER_ID, ACCOUNT_ID);

        assertThat(resolution.accessToken()).isEqualTo("new-access-token");

        ArgumentCaptor<String> access = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refresh = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateTokens(
                eq(ACCOUNT_ID),
                access.capture(),
                refresh.capture(),
                eq(NOW.plusSeconds(86400L)),
                eq(NOW.plus(Duration.ofDays(30))),
                eq("유저 조회")
        );
        // 회전된 Refresh Token까지 반드시 함께 저장해야 다음 갱신이 가능하다.
        assertThat(tokenCipher.decrypt(access.getValue())).isEqualTo("new-access-token");
        assertThat(tokenCipher.decrypt(refresh.getValue())).isEqualTo("new-refresh-token");
    }

    @Test
    void marksReauthRequiredWhenPlatformRejectsRefreshToken() {
        givenAccount(NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(20)), "CONNECTED");
        when(chzzkOAuthClient.refreshToken(anyString()))
                .thenThrow(new ChzzkTokenRejectedException("401 INVALID_TOKEN"));

        TokenResolution resolution = refresher.resolve(STREAMER_ID, ACCOUNT_ID);

        // 예외 대신 결과로 알려야 REAUTH_REQUIRED 기록이 롤백되지 않는다.
        assertThat(resolution.reauthRequired()).isTrue();
        verify(mapper).updateConnectionStatus(ACCOUNT_ID, "REAUTH_REQUIRED");
    }

    @Test
    void skipsPlatformCallWhenRefreshTokenAlreadyExpired() {
        givenAccount(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofHours(1)), "CONNECTED");

        TokenResolution resolution = refresher.resolve(STREAMER_ID, ACCOUNT_ID);

        assertThat(resolution.reauthRequired()).isTrue();
        verify(mapper).updateConnectionStatus(ACCOUNT_ID, "REAUTH_REQUIRED");
        verify(chzzkOAuthClient, never()).refreshToken(anyString());
    }

    @Test
    void returnsReauthRequiredWithoutTouchingPlatformWhenAlreadyFlagged() {
        givenAccount(NOW.plus(Duration.ofHours(12)), NOW.plus(Duration.ofDays(20)), "REAUTH_REQUIRED");

        assertThat(refresher.resolve(STREAMER_ID, ACCOUNT_ID).reauthRequired()).isTrue();
        verifyNoInteractions(chzzkOAuthClient);
        verify(mapper, never()).updateConnectionStatus(anyLong(), any());
    }

    @Test
    void hidesAccountsOwnedByAnotherStreamer() {
        givenAccount(NOW.plus(Duration.ofHours(12)), NOW.plus(Duration.ofDays(20)), "CONNECTED");

        assertThatThrownBy(() -> refresher.resolve(STREAMER_ID + 1, ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");
    }

    private void givenAccount(Instant accessExpiresAt, Instant refreshExpiresAt, String status) {
        when(mapper.findSecretForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(new PlatformAccountSecret(
                ACCOUNT_ID,
                STREAMER_ID,
                "CHZZK",
                "channel-id",
                tokenCipher.encrypt("stored-access-token"),
                tokenCipher.encrypt("stored-refresh-token"),
                accessExpiresAt,
                refreshExpiresAt,
                status
        )));
    }

    private PlatformProperties properties() {
        return new PlatformProperties(
                new PlatformProperties.Chzzk("id", "secret", "uri", "auth", "token", "revoke", "me"),
                KEY,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );
    }
}

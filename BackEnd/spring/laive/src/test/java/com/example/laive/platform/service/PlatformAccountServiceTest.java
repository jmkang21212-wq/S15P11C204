package com.example.laive.platform.service;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.exception.PlatformCallbackException;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.oauth.ChzzkTokenRejectedException;
import com.example.laive.platform.oauth.ChzzkTokenResponse;
import com.example.laive.platform.oauth.ChzzkUser;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.platform.service.PlatformAccountService.CompletedLink;
import com.example.laive.platform.service.PlatformLinkStateService.LinkState;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAccountServiceTest {

    private static final long STREAMER_ID = 7L;
    private static final long ACCOUNT_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final String RETURN_URI = "http://localhost:3000/oauth/callback";

    private PlatformAccountMapper mapper;
    private PlatformLinkStateService linkStateService;
    private PlatformTokenRefresher tokenRefresher;
    private ChzzkOAuthClient chzzkOAuthClient;
    private TokenCipher tokenCipher;
    private PlatformAccountService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PlatformAccountMapper.class);
        linkStateService = mock(PlatformLinkStateService.class);
        tokenRefresher = mock(PlatformTokenRefresher.class);
        chzzkOAuthClient = mock(ChzzkOAuthClient.class);
        tokenCipher = new TokenCipher(properties());
        service = new PlatformAccountService(
                mapper,
                linkStateService,
                tokenRefresher,
                chzzkOAuthClient,
                tokenCipher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void storesEncryptedTokensWhenLinkCompletes() {
        givenCallbackState();
        when(chzzkOAuthClient.issueToken("auth-code", "state-value")).thenReturn(
                new ChzzkTokenResponse("access-token", "refresh-token", "Bearer", 86400L, "유저 조회")
        );
        when(chzzkOAuthClient.fetchMe("access-token"))
                .thenReturn(new ChzzkUser("channel-id", "한별TV", "한별TV"));
        when(mapper.upsert(any())).thenReturn(Optional.of(ACCOUNT_ID));

        CompletedLink completed = service.completeLink("state-value", "auth-code", null);

        assertThat(completed.returnUri()).isEqualTo(RETURN_URI);
        assertThat(completed.account().platformAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(completed.account().channelName()).isEqualTo("한별TV");

        ArgumentCaptor<PlatformAccountUpsert> saved = ArgumentCaptor.forClass(PlatformAccountUpsert.class);
        verify(mapper).upsert(saved.capture());
        PlatformAccountUpsert upsert = saved.getValue();
        // 연동 주체는 콜백 파라미터가 아니라 state에 묶인 streamerId여야 한다.
        assertThat(upsert.streamerId()).isEqualTo(STREAMER_ID);
        assertThat(upsert.accessToken()).isNotEqualTo("access-token");
        assertThat(tokenCipher.decrypt(upsert.accessToken())).isEqualTo("access-token");
        assertThat(tokenCipher.decrypt(upsert.refreshToken())).isEqualTo("refresh-token");
        assertThat(upsert.accessTokenExpiresAt()).isEqualTo(NOW.plusSeconds(86400L));
        assertThat(upsert.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void rejectsChannelAlreadyLinkedToAnotherStreamer() {
        givenCallbackState();
        when(chzzkOAuthClient.issueToken(anyString(), anyString())).thenReturn(
                new ChzzkTokenResponse("access-token", "refresh-token", "Bearer", 86400L, "유저 조회")
        );
        when(chzzkOAuthClient.fetchMe(anyString()))
                .thenReturn(new ChzzkUser("channel-id", "한별TV", "한별TV"));
        // 충돌한 행이 남의 것이면 UPSERT가 아무 행도 돌려주지 않는다.
        when(mapper.upsert(any())).thenReturn(Optional.empty());

        // 복귀 주소를 확보한 뒤의 실패는 리다이렉트할 수 있도록 감싸서 던진다.
        assertThatThrownBy(() -> service.completeLink("state-value", "auth-code", null))
                .isInstanceOf(PlatformCallbackException.class)
                .satisfies(thrown -> {
                    PlatformCallbackException failure = (PlatformCallbackException) thrown;
                    assertThat(failure.getCode()).isEqualTo("PLATFORM_ALREADY_LINKED");
                    assertThat(failure.getReturnUri()).isEqualTo(RETURN_URI);
                });
    }

    @Test
    void stopsBeforeIssuingTokenWhenUserDeclined() {
        givenCallbackState();

        assertThatThrownBy(() -> service.completeLink("state-value", null, "access_denied"))
                .isInstanceOf(PlatformCallbackException.class)
                .extracting("returnUri")
                .isEqualTo(RETURN_URI);

        verify(chzzkOAuthClient, never()).issueToken(anyString(), anyString());
    }

    @Test
    void treatsFreshTokenRejectionAsPlatformFailure() {
        givenCallbackState();
        when(chzzkOAuthClient.issueToken(anyString(), anyString())).thenReturn(
                new ChzzkTokenResponse("access-token", "refresh-token", "Bearer", 86400L, "유저 조회")
        );
        when(chzzkOAuthClient.fetchMe(anyString()))
                .thenThrow(new ChzzkTokenRejectedException("401 INVALID_TOKEN"));

        assertThatThrownBy(() -> service.completeLink("state-value", "auth-code", null))
                .isInstanceOf(PlatformCallbackException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_CONNECTION_FAILED");
    }

    @Test
    void completesUnlinkEvenWhenRevokeFails() {
        givenStoredAccount();
        when(mapper.delete(ACCOUNT_ID, STREAMER_ID)).thenReturn(1);
        doThrow(new ChzzkTokenRejectedException("401 INVALID_TOKEN"))
                .when(chzzkOAuthClient).revokeToken(anyString(), anyString());

        service.unlink(STREAMER_ID, ACCOUNT_ID);

        verify(mapper).delete(ACCOUNT_ID, STREAMER_ID);
        verify(chzzkOAuthClient).revokeToken("stored-access-token", "access_token");
    }

    @Test
    void hidesAccountsOwnedByAnotherStreamerOnUnlink() {
        when(mapper.findSecretByIdAndStreamerId(ACCOUNT_ID, STREAMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(STREAMER_ID, ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");
    }

    @Test
    void mapsReauthResolutionToConflict() {
        when(tokenRefresher.resolve(STREAMER_ID, ACCOUNT_ID))
                .thenReturn(new TokenResolution(null, true));

        assertThatThrownBy(() -> service.accessToken(STREAMER_ID, ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_REAUTH_REQUIRED");
    }

    @Test
    void rejectsUnsupportedPlatform() {
        assertThatThrownBy(() -> service.startLink(STREAMER_ID, "SOOP", RETURN_URI))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_INVALID_REQUEST");
    }

    private void givenCallbackState() {
        when(linkStateService.consume("state-value"))
                .thenReturn(new LinkState(STREAMER_ID, "CHZZK", RETURN_URI));
    }

    private void givenStoredAccount() {
        when(mapper.findSecretByIdAndStreamerId(ACCOUNT_ID, STREAMER_ID)).thenReturn(Optional.of(
                new PlatformAccountSecret(
                        ACCOUNT_ID,
                        STREAMER_ID,
                        "CHZZK",
                        "channel-id",
                        tokenCipher.encrypt("stored-access-token"),
                        tokenCipher.encrypt("stored-refresh-token"),
                        NOW.plus(Duration.ofHours(12)),
                        NOW.plus(Duration.ofDays(20)),
                        "CONNECTED"
                )
        ));
    }

    private PlatformProperties properties() {
        return new PlatformProperties(
                new PlatformProperties.Chzzk("id", "secret", "uri", "auth", "token", "revoke", "me"),
                "dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );
    }
}

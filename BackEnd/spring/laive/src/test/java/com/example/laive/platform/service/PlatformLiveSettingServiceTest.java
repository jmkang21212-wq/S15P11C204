package com.example.laive.platform.service;

import com.example.laive.platform.core.PlatformAdapterRegistry;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformIntegrationAdapter;
import com.example.laive.platform.core.PlatformLiveSetting;
import com.example.laive.platform.core.PlatformLiveSettingCapability;
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import com.example.laive.platform.core.PlatformScopeInsufficientException;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformLiveSettingServiceTest {

    private static final long STREAMER_ID = 7L;
    private static final long PLATFORM_ACCOUNT_ID = 15L;
    private static final String ACCESS_TOKEN = "access-token-value";

    @Mock
    private PlatformAccountMapper platformAccountMapper;

    @Mock
    private PlatformAccountService platformAccountService;

    @Mock
    private PlatformTokenRefresher tokenRefresher;

    @Mock
    private PlatformAdapterRegistry adapterRegistry;

    @Mock
    private PlatformIntegrationAdapter adapter;

    @Mock
    private PlatformLiveSettingCapability capability;

    private PlatformLiveSettingService service;

    @BeforeEach
    void setUp() {
        service = new PlatformLiveSettingService(
                platformAccountMapper, platformAccountService, tokenRefresher, adapterRegistry);
    }

    @Test
    void failsWhenAccountNotOwned() {
        when(platformAccountMapper.findSecretByIdAndStreamerId(PLATFORM_ACCOUNT_ID, STREAMER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");
    }

    @Test
    void failsWhenPlatformDoesNotSupportLiveSetting() {
        givenOwnedAccount();
        when(adapter.liveSetting()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_INVALID_REQUEST");
    }

    @Test
    void returnsLiveSetting() {
        givenCapableAccount();
        PlatformLiveSetting setting = new PlatformLiveSetting("오늘 방송", null, List.of("태그"));
        when(capability.fetchLiveSetting(ACCESS_TOKEN)).thenReturn(setting);

        assertThat(service.get(STREAMER_ID, PLATFORM_ACCOUNT_ID)).isEqualTo(setting);
    }

    @Test
    void mapsScopeInsufficientWithoutTouchingConnectionStatus() {
        givenCapableAccount();
        when(capability.fetchLiveSetting(ACCESS_TOKEN)).thenThrow(
                new PlatformScopeInsufficientException("scope", new RuntimeException()));

        assertThatThrownBy(() -> service.get(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_SCOPE_INSUFFICIENT");

        // 토큰은 다른 API에 여전히 유효하므로 연동 상태를 만료로 표시하면 안 된다.
        verify(tokenRefresher, never()).markReauthRequired(anyLong(), anyString());
    }

    @Test
    void marksReauthRequiredWhenTokenRejected() {
        givenCapableAccount();
        when(capability.fetchLiveSetting(ACCESS_TOKEN)).thenThrow(
                new PlatformCredentialRejectedException("rejected", new RuntimeException()));

        assertThatThrownBy(() -> service.get(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_REAUTH_REQUIRED");

        verify(tokenRefresher).markReauthRequired(anyLong(), anyString());
    }

    @Test
    void rejectsBlankTitleBeforeCallingPlatform() {
        assertThatThrownBy(() -> service.update(STREAMER_ID, PLATFORM_ACCOUNT_ID, "   "))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_INVALID_REQUEST");

        verifyNoInteractions(capability, platformAccountService);
    }

    @Test
    void rejectsTooLongTitleBeforeCallingPlatform() {
        String tooLong = "가".repeat(PlatformLiveSettingService.TITLE_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.update(STREAMER_ID, PLATFORM_ACCOUNT_ID, tooLong))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_INVALID_REQUEST");

        verifyNoInteractions(capability, platformAccountService);
    }

    @Test
    void trimsTitleAndReflectsRequestedValue() {
        givenCapableAccount();

        PlatformLiveSetting result = service.update(STREAMER_ID, PLATFORM_ACCOUNT_ID, "  새 방송 제목  ");

        verify(capability).updateLiveSetting(ACCESS_TOKEN, new PlatformLiveSettingUpdate("새 방송 제목"));
        // 변경 성공 응답에는 content가 없어 요청값을 그대로 돌려준다.
        assertThat(result.defaultLiveTitle()).isEqualTo("새 방송 제목");
    }

    private void givenOwnedAccount() {
        when(platformAccountMapper.findSecretByIdAndStreamerId(PLATFORM_ACCOUNT_ID, STREAMER_ID))
                .thenReturn(Optional.of(new PlatformAccountSecret(
                        PLATFORM_ACCOUNT_ID, STREAMER_ID, "CHZZK", "channel-id",
                        null, null, null, null, "CONNECTED"
                )));
        when(adapterRegistry.require("CHZZK")).thenReturn(adapter);
    }

    private void givenCapableAccount() {
        givenOwnedAccount();
        when(adapter.liveSetting()).thenReturn(Optional.of(capability));
        when(platformAccountService.accessToken(STREAMER_ID, PLATFORM_ACCOUNT_ID)).thenReturn(ACCESS_TOKEN);
    }
}

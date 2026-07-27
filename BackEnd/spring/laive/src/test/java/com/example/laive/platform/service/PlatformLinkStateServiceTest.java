package com.example.laive.platform.service;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.service.PlatformLinkStateService.LinkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformLinkStateServiceTest {

    private static final String RETURN_URI = "http://localhost:3000/oauth/callback";

    private ValueOperations<String, String> valueOperations;
    private PlatformLinkStateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new PlatformLinkStateService(
                redisTemplate,
                platformProperties(),
                authProperties()
        );
    }

    @Test
    void bindsStreamerToStateSoCallbackCannotChooseAccount() {
        String state = service.create(42L, "CHZZK", RETURN_URI);

        assertThat(state).isNotBlank();
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                eq("platform:link:state:" + state),
                stored.capture(),
                eq(Duration.ofMinutes(5))
        );
        assertThat(stored.getValue()).isEqualTo("42|CHZZK|" + RETURN_URI);
    }

    @Test
    void rejectsReturnUriOutsideAllowList() {
        assertThatThrownBy(() -> service.create(42L, "CHZZK", "https://attacker.example/callback"))
                .isInstanceOf(PlatformException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_INVALID_REQUEST");
    }

    @Test
    void consumesStateOnlyOnce() {
        when(valueOperations.getAndDelete(anyString()))
                .thenReturn("42|CHZZK|" + RETURN_URI)
                .thenReturn(null);

        LinkState linkState = service.consume("state-value");

        assertThat(linkState.streamerId()).isEqualTo(42L);
        assertThat(linkState.platform()).isEqualTo("CHZZK");
        assertThat(linkState.returnUri()).isEqualTo(RETURN_URI);
        assertThatThrownBy(() -> service.consume("state-value"))
                .isInstanceOf(PlatformException.class);
    }

    @Test
    void rejectsStateWhoseReturnUriIsNoLongerAllowed() {
        when(valueOperations.getAndDelete(anyString()))
                .thenReturn("42|CHZZK|https://attacker.example");

        assertThatThrownBy(() -> service.consume("state-value"))
                .isInstanceOf(PlatformException.class);
    }

    private PlatformProperties platformProperties() {
        return new PlatformProperties(
                new PlatformProperties.Chzzk("id", "secret", "uri", "auth", "token", "revoke", "me"),
                "dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );
    }

    private AuthProperties authProperties() {
        return new AuthProperties(
                new AuthProperties.Naver("", "", "", "", "", ""),
                new AuthProperties.Jwt("01234567890123456789012345678901", Duration.ofMinutes(30), "laive"),
                new AuthProperties.RefreshCookie("laive_refresh", Duration.ofDays(14), false),
                List.of(RETURN_URI),
                Duration.ofMinutes(5)
        );
    }
}

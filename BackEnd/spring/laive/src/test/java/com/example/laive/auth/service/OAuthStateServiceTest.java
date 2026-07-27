package com.example.laive.auth.service;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthStateServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OAuthStateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new OAuthStateService(redisTemplate, properties());
    }

    @Test
    void storesAllowedReturnUriWithFiveMinuteTtl() {
        String state = service.create("http://localhost:3000/oauth/callback");

        assertThat(state).isNotBlank();
        verify(valueOperations).set(
                "auth:oauth:state:" + state,
                "http://localhost:3000/oauth/callback",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void rejectsReturnUriOutsideAllowList() {
        assertThatThrownBy(() -> service.create("https://attacker.example/callback"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("AUTH_INVALID_REQUEST");
    }

    @Test
    void consumesStateOnlyOnce() {
        when(redisTemplate.opsForValue().getAndDelete(anyString()))
                .thenReturn("http://localhost:3000/oauth/callback")
                .thenReturn(null);

        assertThat(service.consume("state")).isEqualTo("http://localhost:3000/oauth/callback");
        assertThatThrownBy(() -> service.consume("state"))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    private AuthProperties properties() {
        return new AuthProperties(
                new AuthProperties.Naver("", "", "", "", "", ""),
                new AuthProperties.Jwt("01234567890123456789012345678901", Duration.ofMinutes(30), "laive"),
                new AuthProperties.RefreshCookie("laive_refresh", Duration.ofDays(14), false),
                List.of("http://localhost:3000/oauth/callback"),
                Duration.ofMinutes(5)
        );
    }
}

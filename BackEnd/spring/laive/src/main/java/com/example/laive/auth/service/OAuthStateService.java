package com.example.laive.auth.service;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.auth.exception.AuthException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * OAuth 요청 위조와 재전송을 막기 위한 일회용 state를 Redis에서 관리한다.
 */
@Service
public class OAuthStateService {

    private static final String KEY_PREFIX = "auth:oauth:state:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthStateService(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 허용 목록의 복귀 주소만 state와 함께 Redis에 제한 시간 동안 저장한다.
     */
    public String create(String returnUri) {
        if (returnUri == null || !properties.allowedReturnUris().contains(returnUri)) {
            throw AuthException.invalidRequest("허용되지 않은 returnUri입니다.");
        }

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        redisTemplate.opsForValue().set(KEY_PREFIX + state, returnUri, properties.oauthStateTtl());
        return state;
    }

    /**
     * state를 원자적으로 조회·삭제해 한 번만 사용할 수 있도록 보장한다.
     */
    public String consume(String state) {
        if (state == null || state.isBlank()) {
            throw AuthException.invalidCredentials();
        }
        String returnUri = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
        if (returnUri == null || !properties.allowedReturnUris().contains(returnUri)) {
            throw AuthException.invalidCredentials();
        }
        return returnUri;
    }
}

package com.example.laive.platform.service;

import com.example.laive.auth.config.AuthProperties;
import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.exception.PlatformException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 플랫폼 연동 요청의 일회용 state를 Redis에서 관리한다.
 *
 * 콜백은 브라우저 리다이렉트로 들어와 Access Token을 실을 수 없다.
 * 어느 회원의 연동인지는 반드시 여기 저장된 streamerId로 복원해야 하며,
 * 콜백 파라미터나 쿠키로 받으면 타인 계정에 채널을 붙이는 공격이 가능하다.
 */
@Service
public class PlatformLinkStateService {

    private static final String KEY_PREFIX = "platform:link:state:";

    /** returnUri는 마지막 조각으로 통째로 읽으므로 구분자를 포함해도 안전하다. */
    private static final String DELIMITER = "|";

    private final StringRedisTemplate redisTemplate;
    private final PlatformProperties platformProperties;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PlatformLinkStateService(
            StringRedisTemplate redisTemplate,
            PlatformProperties platformProperties,
            AuthProperties authProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.platformProperties = platformProperties;
        this.authProperties = authProperties;
    }

    /**
     * 허용 목록의 복귀 주소만 연동 주체와 함께 제한 시간 동안 저장한다.
     * 복귀 주소 허용 목록은 소셜 로그인과 같은 것을 사용한다.
     */
    public String create(long streamerId, String platform, String returnUri) {
        if (returnUri == null || !authProperties.allowedReturnUris().contains(returnUri)) {
            throw PlatformException.invalidRequest("허용되지 않은 returnUri입니다.");
        }

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        redisTemplate.opsForValue().set(
                KEY_PREFIX + state,
                serialize(new LinkState(streamerId, platform, returnUri)),
                platformProperties.linkStateTtl()
        );
        return state;
    }

    /**
     * state를 원자적으로 조회·삭제해 한 번만 사용할 수 있도록 보장한다.
     */
    public LinkState consume(String state) {
        if (state == null || state.isBlank()) {
            throw PlatformException.invalidRequest("유효하지 않은 요청입니다.");
        }
        String stored = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
        if (stored == null) {
            throw PlatformException.invalidRequest("만료되었거나 이미 사용된 요청입니다.");
        }

        LinkState linkState = deserialize(stored);
        if (!authProperties.allowedReturnUris().contains(linkState.returnUri())) {
            // 저장 후 허용 목록이 바뀐 경우까지 방어한다.
            throw PlatformException.invalidRequest("허용되지 않은 returnUri입니다.");
        }
        return linkState;
    }

    private String serialize(LinkState linkState) {
        return linkState.streamerId() + DELIMITER + linkState.platform() + DELIMITER + linkState.returnUri();
    }

    private LinkState deserialize(String stored) {
        String[] parts = stored.split("\\" + DELIMITER, 3);
        if (parts.length != 3) {
            throw PlatformException.invalidRequest("유효하지 않은 요청입니다.");
        }
        try {
            return new LinkState(Long.parseLong(parts[0]), parts[1], parts[2]);
        } catch (NumberFormatException exception) {
            throw PlatformException.invalidRequest("유효하지 않은 요청입니다.");
        }
    }

    public record LinkState(long streamerId, String platform, String returnUri) {
    }
}

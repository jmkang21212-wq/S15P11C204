package com.example.laive.platform.service;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.oauth.ChzzkTokenRejectedException;
import com.example.laive.platform.oauth.ChzzkTokenResponse;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 플랫폼 Access Token을 필요한 시점에만 갱신해서 돌려준다.
 *
 * 치지직은 갱신에 성공하는 즉시 이전 Access Token과 Refresh Token을 모두 무효화하고
 * 새 Refresh Token은 1회용이다. 따라서 "만료 확인 -> 갱신 -> 저장"이 한 트랜잭션 안에서
 * 행 잠금과 함께 이뤄져야 하며, 응답을 저장하지 못하면 연동이 끊긴다.
 * 동시에 들어온 요청은 잠금 해제 후 갱신된 행을 다시 읽어 갱신을 건너뛴다.
 *
 * 재연동이 필요해진 경우 예외를 던지지 않고 결과로 알린다.
 * 여기서 예외를 던지면 방금 기록한 REAUTH_REQUIRED 상태까지 롤백되기 때문이다.
 */
@Service
public class PlatformTokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(PlatformTokenRefresher.class);
    private static final String REAUTH_REQUIRED = "REAUTH_REQUIRED";

    private final PlatformAccountMapper platformAccountMapper;
    private final ChzzkOAuthClient chzzkOAuthClient;
    private final TokenCipher tokenCipher;
    private final PlatformProperties properties;
    private final Clock clock;

    public PlatformTokenRefresher(
            PlatformAccountMapper platformAccountMapper,
            ChzzkOAuthClient chzzkOAuthClient,
            TokenCipher tokenCipher,
            PlatformProperties properties,
            Clock clock
    ) {
        this.platformAccountMapper = platformAccountMapper;
        this.chzzkOAuthClient = chzzkOAuthClient;
        this.tokenCipher = tokenCipher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TokenResolution resolve(long streamerId, long platformAccountId) {
        PlatformAccountSecret secret = platformAccountMapper.findSecretForUpdate(platformAccountId)
                // 남의 연동 ID를 넣어도 존재 여부를 알려주지 않는다.
                .filter(account -> account.streamerId() == streamerId)
                .orElseThrow(PlatformException::notFound);

        if (!PlatformAccountService.CHZZK.equals(secret.platform())) {
            throw PlatformException.invalidRequest("아직 지원하지 않는 플랫폼입니다.");
        }
        if (secret.isReauthRequired()) {
            return TokenResolution.requiringReauth();
        }

        Instant now = clock.instant();
        if (isAccessTokenUsable(secret, now)) {
            return TokenResolution.of(tokenCipher.decrypt(secret.accessToken()));
        }
        if (secret.refreshTokenExpiresAt() != null && !now.isBefore(secret.refreshTokenExpiresAt())) {
            return markReauthRequired(platformAccountId, "Refresh Token 만료");
        }

        ChzzkTokenResponse refreshed;
        try {
            refreshed = chzzkOAuthClient.refreshToken(tokenCipher.decrypt(secret.refreshToken()));
        } catch (ChzzkTokenRejectedException exception) {
            return markReauthRequired(platformAccountId, "플랫폼이 Refresh Token을 거부함");
        }

        platformAccountMapper.updateTokens(
                platformAccountId,
                tokenCipher.encrypt(refreshed.accessToken()),
                tokenCipher.encrypt(refreshed.refreshToken()),
                now.plusSeconds(refreshed.expiresIn()),
                now.plus(PlatformAccountService.REFRESH_TOKEN_TTL),
                refreshed.scope()
        );
        return TokenResolution.of(refreshed.accessToken());
    }

    /**
     * 만료 임박(기본 5분) 전까지만 기존 토큰을 재사용한다.
     * 만료 시각을 모르는 행은 갱신 대상으로 본다.
     */
    private boolean isAccessTokenUsable(PlatformAccountSecret secret, Instant now) {
        Instant expiresAt = secret.accessTokenExpiresAt();
        return expiresAt != null
                && now.isBefore(expiresAt.minus(properties.accessTokenRefreshThreshold()));
    }

    private TokenResolution markReauthRequired(long platformAccountId, String reason) {
        log.info("플랫폼 재연동 필요: platformAccountId={} reason={}", platformAccountId, reason);
        platformAccountMapper.updateConnectionStatus(platformAccountId, REAUTH_REQUIRED);
        return TokenResolution.requiringReauth();
    }

    /**
     * 복호화된 Access Token 또는 재연동 필요 신호를 전달한다.
     */
    public record TokenResolution(String accessToken, boolean reauthRequired) {

        static TokenResolution of(String accessToken) {
            return new TokenResolution(accessToken, false);
        }

        static TokenResolution requiringReauth() {
            return new TokenResolution(null, true);
        }
    }
}

package com.example.laive.platform.service;

import com.example.laive.platform.chzzk.ChzzkPlatformAdapter;
import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.core.PlatformAdapterRegistry;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformOAuthCapability;
import com.example.laive.platform.core.PlatformOAuthTokens;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 플랫폼 Adapter를 통해 만료가 임박한 credential을 직렬화해 갱신한다. */
@Service
public class PlatformTokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(PlatformTokenRefresher.class);
    private static final String REAUTH_REQUIRED = "REAUTH_REQUIRED";

    private final PlatformAccountMapper platformAccountMapper;
    private final PlatformAdapterRegistry adapterRegistry;
    private final TokenCipher tokenCipher;
    private final PlatformProperties properties;
    private final Clock clock;

    @Autowired
    public PlatformTokenRefresher(
            PlatformAccountMapper platformAccountMapper,
            PlatformAdapterRegistry adapterRegistry,
            TokenCipher tokenCipher,
            PlatformProperties properties,
            Clock clock
    ) {
        this.platformAccountMapper = platformAccountMapper;
        this.adapterRegistry = adapterRegistry;
        this.tokenCipher = tokenCipher;
        this.properties = properties;
        this.clock = clock;
    }

    /** 기존 CHZZK 단위 테스트의 소스 호환을 위한 생성자다. */
    public PlatformTokenRefresher(
            PlatformAccountMapper platformAccountMapper,
            ChzzkOAuthClient chzzkOAuthClient,
            TokenCipher tokenCipher,
            PlatformProperties properties,
            Clock clock
    ) {
        this(
                platformAccountMapper,
                new PlatformAdapterRegistry(List.of(new ChzzkPlatformAdapter(chzzkOAuthClient, clock))),
                tokenCipher,
                properties,
                clock
        );
    }

    @Transactional
    public TokenResolution resolve(long streamerId, long platformAccountId) {
        PlatformAccountSecret secret = platformAccountMapper.findSecretForUpdate(platformAccountId)
                .filter(account -> account.streamerId() == streamerId)
                .orElseThrow(PlatformException::notFound);
        if (secret.isReauthRequired() || "DISCONNECTED".equals(secret.connectionStatus())) {
            return TokenResolution.requiringReauth();
        }

        Instant now = clock.instant();
        if (isAccessTokenUsable(secret, now)) {
            return TokenResolution.of(tokenCipher.decrypt(secret.accessToken()));
        }
        if (secret.refreshToken() == null
                || secret.refreshTokenExpiresAt() != null && !now.isBefore(secret.refreshTokenExpiresAt())) {
            return markReauthRequired(platformAccountId, "refresh credential unavailable or expired");
        }

        PlatformOAuthCapability oauth = adapterRegistry.require(secret.platform()).oauth()
                .orElseThrow(() -> PlatformException.invalidRequest("OAuth 갱신을 지원하지 않는 플랫폼입니다."));

        String oldRefreshToken = tokenCipher.decrypt(secret.refreshToken());
        PlatformOAuthTokens refreshed;
        try {
            refreshed = oauth.refresh(oldRefreshToken);
        } catch (PlatformCredentialRejectedException exception) {
            return markReauthRequired(platformAccountId, "platform rejected refresh credential");
        }

        String refreshToken = refreshed.refreshToken() == null
                ? oldRefreshToken : refreshed.refreshToken();
        Instant refreshExpiresAt = refreshed.refreshTokenExpiresAt() == null
                ? secret.refreshTokenExpiresAt() : refreshed.refreshTokenExpiresAt();
        platformAccountMapper.updateTokens(
                platformAccountId,
                tokenCipher.encrypt(refreshed.accessToken()),
                tokenCipher.encrypt(refreshToken),
                refreshed.accessTokenExpiresAt(),
                refreshExpiresAt,
                refreshed.scope()
        );
        return TokenResolution.of(refreshed.accessToken());
    }

    private boolean isAccessTokenUsable(PlatformAccountSecret secret, Instant now) {
        Instant expiresAt = secret.accessTokenExpiresAt();
        return secret.accessToken() != null
                && expiresAt != null
                && now.isBefore(expiresAt.minus(properties.accessTokenRefreshThreshold()));
    }

    /**
     * 갱신 실패 외에 API 호출 시점에 토큰이 거부(401)된 경우에도
     * 재연동 필요 상태를 기록할 수 있도록 공개한다.
     */
    @Transactional
    public TokenResolution markReauthRequired(long platformAccountId, String reason) {
        log.info("Platform reauthorization required: platformAccountId={} reason={}", platformAccountId, reason);
        platformAccountMapper.updateConnectionStatus(platformAccountId, REAUTH_REQUIRED);
        return TokenResolution.requiringReauth();
    }

    public record TokenResolution(String accessToken, boolean reauthRequired) {

        static TokenResolution of(String accessToken) {
            return new TokenResolution(accessToken, false);
        }

        static TokenResolution requiringReauth() {
            return new TokenResolution(null, true);
        }
    }
}

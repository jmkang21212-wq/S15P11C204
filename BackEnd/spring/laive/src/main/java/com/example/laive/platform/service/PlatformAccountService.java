package com.example.laive.platform.service;

import com.example.laive.platform.chzzk.ChzzkPlatformAdapter;
import com.example.laive.platform.core.PlatformAdapterRegistry;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformIdentity;
import com.example.laive.platform.core.PlatformIntegrationAdapter;
import com.example.laive.platform.core.PlatformOAuthCapability;
import com.example.laive.platform.core.PlatformOAuthTokens;
import com.example.laive.platform.domain.PlatformAccount;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.exception.PlatformCallbackException;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.platform.service.PlatformLinkStateService.LinkState;
import com.example.laive.platform.service.PlatformTokenRefresher.TokenResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 플랫폼 Adapter를 통해 계정 연동·조회·해제를 처리한다. */
@Service
public class PlatformAccountService {

    public static final String CHZZK = "CHZZK";

    private static final Logger log = LoggerFactory.getLogger(PlatformAccountService.class);

    private final PlatformAccountMapper platformAccountMapper;
    private final PlatformLinkStateService linkStateService;
    private final PlatformTokenRefresher tokenRefresher;
    private final PlatformAdapterRegistry adapterRegistry;
    private final TokenCipher tokenCipher;
    private final Clock clock;

    @Autowired
    public PlatformAccountService(
            PlatformAccountMapper platformAccountMapper,
            PlatformLinkStateService linkStateService,
            PlatformTokenRefresher tokenRefresher,
            PlatformAdapterRegistry adapterRegistry,
            TokenCipher tokenCipher,
            Clock clock
    ) {
        this.platformAccountMapper = platformAccountMapper;
        this.linkStateService = linkStateService;
        this.tokenRefresher = tokenRefresher;
        this.adapterRegistry = adapterRegistry;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    /** 기존 CHZZK 단위 테스트의 소스 호환을 위한 생성자다. */
    public PlatformAccountService(
            PlatformAccountMapper platformAccountMapper,
            PlatformLinkStateService linkStateService,
            PlatformTokenRefresher tokenRefresher,
            ChzzkOAuthClient chzzkOAuthClient,
            TokenCipher tokenCipher,
            Clock clock
    ) {
        this(
                platformAccountMapper,
                linkStateService,
                tokenRefresher,
                new PlatformAdapterRegistry(List.of(new ChzzkPlatformAdapter(chzzkOAuthClient, clock))),
                tokenCipher,
                clock
        );
    }

    public String startLink(long streamerId, String platform, String returnUri) {
        PlatformIntegrationAdapter adapter = adapterRegistry.require(platform);
        PlatformOAuthCapability oauth = requireOAuth(adapter);
        String state = linkStateService.create(streamerId, adapter.platformCode().value(), returnUri);
        return oauth.buildAuthorizationUri(state);
    }

    public CompletedLink completeLink(String state, String code, String error) {
        LinkState linkState = linkStateService.consume(state);
        try {
            return completeLink(linkState, state, code, error);
        } catch (PlatformException exception) {
            throw new PlatformCallbackException(linkState.returnUri(), exception);
        }
    }

    private CompletedLink completeLink(LinkState linkState, String state, String code, String error) {
        PlatformIntegrationAdapter adapter = adapterRegistry.require(linkState.platform());
        PlatformOAuthCapability oauth = requireOAuth(adapter);
        if (error != null && !error.isBlank()) {
            log.info("Platform link declined: platform={} error={}", adapter.platformCode(), error);
            throw PlatformException.invalidRequest("플랫폼 연동이 취소되었습니다.");
        }
        if (code == null || code.isBlank()) {
            throw PlatformException.invalidRequest("유효하지 않은 OAuth 요청입니다.");
        }

        PlatformOAuthTokens token = oauth.exchange(code, state);
        PlatformIdentity identity;
        try {
            identity = oauth.fetchIdentity(token.accessToken());
        } catch (PlatformCredentialRejectedException exception) {
            throw PlatformException.connectionFailed();
        }

        Instant now = clock.instant();
        PlatformAccountUpsert upsert = new PlatformAccountUpsert(
                linkState.streamerId(),
                adapter.platformCode().value(),
                identity.externalAccountId(),
                identity.channelName(),
                tokenCipher.encrypt(token.accessToken()),
                encryptNullable(token.refreshToken()),
                token.accessTokenExpiresAt(),
                token.refreshTokenExpiresAt(),
                token.scope()
        );
        long platformAccountId = platformAccountMapper.upsert(upsert)
                .orElseThrow(PlatformException::alreadyLinked);

        return new CompletedLink(
                linkState.returnUri(),
                new PlatformAccount(
                        platformAccountId,
                        adapter.platformCode().value(),
                        identity.externalAccountId(),
                        identity.channelName(),
                        "CONNECTED",
                        now
                )
        );
    }

    public List<PlatformAccount> list(long streamerId) {
        return platformAccountMapper.findByStreamerId(streamerId);
    }

    /** 토큰을 폐기한 뒤 과거 분석 FK를 보존하기 위해 계정은 DISCONNECTED로 남긴다. */
    public void unlink(long streamerId, long platformAccountId) {
        PlatformAccountSecret secret = platformAccountMapper
                .findSecretByIdAndStreamerId(platformAccountId, streamerId)
                .orElseThrow(PlatformException::notFound);

        if (platformAccountMapper.delete(platformAccountId, streamerId) != 1) {
            throw PlatformException.notFound();
        }

        if (secret.accessToken() == null) {
            return;
        }
        try {
            adapterRegistry.require(secret.platform())
                    .oauth()
                    .ifPresent(oauth -> oauth.revoke(tokenCipher.decrypt(secret.accessToken())));
        } catch (PlatformCredentialRejectedException | PlatformException | IllegalStateException exception) {
            log.warn("Platform token revocation failed after local unlink: platformAccountId={} reason={}",
                    platformAccountId, exception.getClass().getSimpleName());
        }
    }

    public String accessToken(long streamerId, long platformAccountId) {
        TokenResolution resolution = tokenRefresher.resolve(streamerId, platformAccountId);
        if (resolution.reauthRequired()) {
            throw PlatformException.reauthRequired();
        }
        return resolution.accessToken();
    }

    private PlatformOAuthCapability requireOAuth(PlatformIntegrationAdapter adapter) {
        return adapter.oauth().orElseThrow(() ->
                PlatformException.invalidRequest("OAuth 연동을 지원하지 않는 플랫폼입니다."));
    }

    private String encryptNullable(String value) {
        return value == null ? null : tokenCipher.encrypt(value);
    }

    public record CompletedLink(String returnUri, PlatformAccount account) {
    }
}

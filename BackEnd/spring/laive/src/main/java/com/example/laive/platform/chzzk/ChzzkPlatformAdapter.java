package com.example.laive.platform.chzzk;

import com.example.laive.platform.core.PlatformCapabilityCode;
import com.example.laive.platform.core.PlatformCode;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformDescriptor;
import com.example.laive.platform.core.PlatformIdentity;
import com.example.laive.platform.core.PlatformIntegrationAdapter;
import com.example.laive.platform.core.PlatformLiveSetting;
import com.example.laive.platform.core.PlatformLiveSettingCapability;
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import com.example.laive.platform.core.PlatformOAuthCapability;
import com.example.laive.platform.core.PlatformOAuthTokens;
import com.example.laive.platform.core.PlatformScopeInsufficientException;
import com.example.laive.platform.oauth.ChzzkLiveSetting;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.oauth.ChzzkScopeInsufficientException;
import com.example.laive.platform.oauth.ChzzkTokenRejectedException;
import com.example.laive.platform.oauth.ChzzkTokenResponse;
import com.example.laive.platform.oauth.ChzzkUser;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/** 기존 CHZZK 구현을 코어 서비스에서 분리하는 Adapter다. */
@Component
public class ChzzkPlatformAdapter
        implements PlatformIntegrationAdapter, PlatformOAuthCapability, PlatformLiveSettingCapability {

    public static final PlatformCode CODE = PlatformCode.of("CHZZK");
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final ChzzkOAuthClient client;
    private final Clock clock;

    public ChzzkPlatformAdapter(ChzzkOAuthClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public PlatformCode platformCode() {
        return CODE;
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                CODE,
                "치지직",
                true,
                Set.of(
                        PlatformCapabilityCode.OAUTH,
                        PlatformCapabilityCode.LIVE_DISCOVERY,
                        PlatformCapabilityCode.CHAT_COLLECTION,
                        PlatformCapabilityCode.VIEWER_METRICS,
                        PlatformCapabilityCode.BROADCAST_SETTINGS
                ),
                null
        );
    }

    @Override
    public Optional<PlatformOAuthCapability> oauth() {
        return Optional.of(this);
    }

    @Override
    public Optional<PlatformLiveSettingCapability> liveSetting() {
        return Optional.of(this);
    }

    @Override
    public String buildAuthorizationUri(String state) {
        return client.buildAuthorizationUri(state);
    }

    @Override
    public PlatformOAuthTokens exchange(String code, String state) {
        return normalize(client.issueToken(code, state));
    }

    @Override
    public PlatformOAuthTokens refresh(String refreshToken) {
        try {
            return normalize(client.refreshToken(refreshToken));
        } catch (ChzzkTokenRejectedException exception) {
            throw new PlatformCredentialRejectedException("CHZZK credential rejected", exception);
        }
    }

    @Override
    public PlatformIdentity fetchIdentity(String accessToken) {
        try {
            ChzzkUser user = client.fetchMe(accessToken);
            return new PlatformIdentity(user.channelId(), user.channelName());
        } catch (ChzzkTokenRejectedException exception) {
            throw new PlatformCredentialRejectedException("CHZZK credential rejected", exception);
        }
    }

    @Override
    public void revoke(String accessToken) {
        try {
            client.revokeToken(accessToken, "access_token");
        } catch (ChzzkTokenRejectedException exception) {
            throw new PlatformCredentialRejectedException("CHZZK credential rejected", exception);
        }
    }

    @Override
    public PlatformLiveSetting fetchLiveSetting(String accessToken) {
        try {
            return normalize(client.fetchLiveSetting(accessToken));
        } catch (ChzzkTokenRejectedException exception) {
            throw new PlatformCredentialRejectedException("CHZZK credential rejected", exception);
        } catch (ChzzkScopeInsufficientException exception) {
            throw new PlatformScopeInsufficientException("CHZZK scope insufficient", exception);
        }
    }

    @Override
    public void updateLiveSetting(String accessToken, PlatformLiveSettingUpdate update) {
        try {
            client.updateLiveSetting(accessToken, update.defaultLiveTitle());
        } catch (ChzzkTokenRejectedException exception) {
            throw new PlatformCredentialRejectedException("CHZZK credential rejected", exception);
        } catch (ChzzkScopeInsufficientException exception) {
            throw new PlatformScopeInsufficientException("CHZZK scope insufficient", exception);
        }
    }

    private PlatformLiveSetting normalize(ChzzkLiveSetting setting) {
        ChzzkLiveSetting.ChzzkLiveCategory category = setting.category();
        return new PlatformLiveSetting(
                setting.defaultLiveTitle(),
                category == null ? null : new PlatformLiveSetting.Category(
                        category.categoryType(),
                        category.categoryId(),
                        category.categoryValue(),
                        category.posterImageUrl()
                ),
                setting.tags()
        );
    }

    private PlatformOAuthTokens normalize(ChzzkTokenResponse token) {
        var now = clock.instant();
        return new PlatformOAuthTokens(
                token.accessToken(),
                token.refreshToken(),
                now.plusSeconds(token.expiresIn()),
                now.plus(REFRESH_TOKEN_TTL),
                token.scope()
        );
    }
}

package com.example.laive.platform.youtube;

import com.example.laive.platform.core.LiveDiscoveryCapability;
import com.example.laive.platform.core.PlatformCapabilityCode;
import com.example.laive.platform.core.PlatformCode;
import com.example.laive.platform.core.PlatformDescriptor;
import com.example.laive.platform.core.PlatformIdentity;
import com.example.laive.platform.core.PlatformIntegrationAdapter;
import com.example.laive.platform.core.PlatformLiveSetting;
import com.example.laive.platform.core.PlatformLiveSettingCapability;
import com.example.laive.platform.core.PlatformLiveSettingUpdate;
import com.example.laive.platform.core.PlatformOAuthCapability;
import com.example.laive.platform.core.PlatformOAuthTokens;
import com.example.laive.platform.core.ViewerMetricCapability;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/** YouTube의 선택 Capability를 조립하는 Adapter다. */
@Component
public class YouTubePlatformAdapter
        implements PlatformIntegrationAdapter, PlatformOAuthCapability, PlatformLiveSettingCapability {

    public static final PlatformCode CODE = PlatformCode.of("YOUTUBE");

    private final YouTubeOAuthClient oauthClient;
    private final YouTubeLiveClient liveClient;

    public YouTubePlatformAdapter(YouTubeOAuthClient oauthClient, YouTubeLiveClient liveClient) {
        this.oauthClient = oauthClient;
        this.liveClient = liveClient;
    }

    @Override
    public PlatformCode platformCode() {
        return CODE;
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                CODE,
                "YouTube",
                oauthClient.configured(),
                Set.of(
                        PlatformCapabilityCode.OAUTH,
                        PlatformCapabilityCode.LIVE_DISCOVERY,
                        PlatformCapabilityCode.CHAT_COLLECTION,
                        PlatformCapabilityCode.VIEWER_METRICS,
                        PlatformCapabilityCode.VIDEO_METADATA,
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
    public Optional<LiveDiscoveryCapability> liveDiscovery() {
        return Optional.of(liveClient::findActiveBroadcast);
    }

    @Override
    public Optional<ViewerMetricCapability> viewerMetrics() {
        return Optional.of(liveClient::fetchViewerCount);
    }

    @Override
    public Optional<PlatformLiveSettingCapability> liveSetting() {
        return Optional.of(this);
    }

    @Override
    public String buildAuthorizationUri(String state) {
        return oauthClient.buildAuthorizationUri(state);
    }

    @Override
    public PlatformOAuthTokens exchange(String code, String state) {
        return oauthClient.exchange(code);
    }

    @Override
    public PlatformOAuthTokens refresh(String refreshToken) {
        return oauthClient.refresh(refreshToken);
    }

    @Override
    public PlatformIdentity fetchIdentity(String accessToken) {
        return oauthClient.fetchIdentity(accessToken);
    }

    @Override
    public void revoke(String accessToken) {
        oauthClient.revoke(accessToken);
    }

    @Override
    public PlatformLiveSetting fetchLiveSetting(String accessToken) {
        return liveClient.fetchLiveSetting(accessToken);
    }

    @Override
    public void updateLiveSetting(String accessToken, PlatformLiveSettingUpdate update) {
        liveClient.updateLiveSetting(accessToken, update.defaultLiveTitle());
    }
}

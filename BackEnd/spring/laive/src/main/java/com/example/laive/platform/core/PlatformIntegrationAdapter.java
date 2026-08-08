package com.example.laive.platform.core;

import java.util.Optional;

/**
 * 플랫폼 통합 진입점이다. 구현체는 지원하는 Capability만 노출한다.
 */
public interface PlatformIntegrationAdapter {

    PlatformCode platformCode();

    PlatformDescriptor descriptor();

    default Optional<PlatformOAuthCapability> oauth() {
        return Optional.empty();
    }

    default Optional<LiveDiscoveryCapability> liveDiscovery() {
        return Optional.empty();
    }

    /** 방송을 켜 두는 앞단. 없으면 사용자가 플랫폼에서 직접 켠 방송을 discovery가 찾는다. */
    default Optional<BroadcastLaunchCapability> broadcastLaunch() {
        return Optional.empty();
    }

    default Optional<ViewerMetricCapability> viewerMetrics() {
        return Optional.empty();
    }

    default Optional<PlatformLiveSettingCapability> liveSetting() {
        return Optional.empty();
    }
}

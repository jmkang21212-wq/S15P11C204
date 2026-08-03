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

    default Optional<ViewerMetricCapability> viewerMetrics() {
        return Optional.empty();
    }
}

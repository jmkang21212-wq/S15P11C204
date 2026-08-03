package com.example.laive.platform.core;

import java.util.Optional;

/** 사용자가 외부 플랫폼에서 이미 시작한 방송을 탐지하는 기능이다. */
public interface LiveDiscoveryCapability {

    Optional<ExternalBroadcast> findActiveBroadcast(String accessToken);
}

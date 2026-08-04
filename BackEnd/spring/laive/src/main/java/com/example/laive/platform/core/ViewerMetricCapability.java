package com.example.laive.platform.core;

import java.util.OptionalLong;

/** 플랫폼의 현재 동시 시청자 수 조회 기능이다. */
public interface ViewerMetricCapability {

    OptionalLong fetchViewerCount(String accessToken, String externalBroadcastId);
}

package com.example.laive.platform.core;

/** 플랫폼 방송 설정(제목 등)을 조회·변경하는 기능이다. */
public interface PlatformLiveSettingCapability {

    PlatformLiveSetting fetchLiveSetting(String accessToken);

    void updateLiveSetting(String accessToken, PlatformLiveSettingUpdate update);
}

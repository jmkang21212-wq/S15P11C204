package com.example.laive.platform.core;

/** 플랫폼 방송 설정 변경 요청이다. 카테고리·태그를 지원하게 되면 필드를 추가한다. */
public record PlatformLiveSettingUpdate(String defaultLiveTitle) {
}

package com.example.laive.platform.api;

/** 방송 설정 변경 요청이다. 값 검증은 서비스 계층이 수행한다. */
public record UpdateLiveSettingRequest(String defaultLiveTitle) {
}

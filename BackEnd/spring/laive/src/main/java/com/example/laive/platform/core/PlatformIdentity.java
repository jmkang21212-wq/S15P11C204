package com.example.laive.platform.core;

/** 플랫폼 계정 연동 후 조회한 채널 identity다. */
public record PlatformIdentity(String externalAccountId, String channelName) {
}

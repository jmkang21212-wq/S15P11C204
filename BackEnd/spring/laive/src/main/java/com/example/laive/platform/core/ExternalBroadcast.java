package com.example.laive.platform.core;

import java.time.Instant;
import java.util.Map;

/** 외부 플랫폼의 현재 라이브를 공통 형식으로 정규화한 결과다. */
public record ExternalBroadcast(
        String externalBroadcastId,
        String title,
        String watchUrl,
        String liveChatId,
        Instant startedAt,
        Map<String, Object> metadata
) {
    public ExternalBroadcast {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

package com.example.laive.broadcast.session.domain;

import java.time.Instant;

/** 논리 방송 세션에 속한 플랫폼 계정별 자식 세션이다. */
public record BroadcastPlatformSession(
        long platformSessionId,
        long broadcastSessionId,
        long platformAccountId,
        String platformCode,
        String channelName,
        PlatformSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant lastSyncedAt,
        String errorCode,
        String watchUrl
) {
}

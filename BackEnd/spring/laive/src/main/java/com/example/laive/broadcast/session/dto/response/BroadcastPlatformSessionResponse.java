package com.example.laive.broadcast.session.dto.response;

import com.example.laive.broadcast.session.domain.BroadcastPlatformSession;
import com.example.laive.broadcast.session.domain.PlatformSessionStatus;

import java.time.Instant;

public record BroadcastPlatformSessionResponse(
        long platformSessionId,
        long platformAccountId,
        String platformCode,
        String channelName,
        PlatformSessionStatus status,
        Instant startedAt,
        Instant endedAt,
        String errorCode,
        String watchUrl
) {
    public static BroadcastPlatformSessionResponse from(BroadcastPlatformSession session) {
        return new BroadcastPlatformSessionResponse(
                session.platformSessionId(), session.platformAccountId(), session.platformCode(),
                session.channelName(), session.status(), session.startedAt(), session.endedAt(),
                session.errorCode(), session.watchUrl()
        );
    }
}

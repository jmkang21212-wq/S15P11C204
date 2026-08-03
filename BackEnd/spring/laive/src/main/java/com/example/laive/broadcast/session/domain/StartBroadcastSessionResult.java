package com.example.laive.broadcast.session.domain;

import java.time.Instant;
import java.util.List;

public record StartBroadcastSessionResult(
        long sessionId,
        BroadcastStatus status,
        Instant startedAt,
        List<BroadcastPlatformSession> platformSessions
) {
    public StartBroadcastSessionResult {
        platformSessions = platformSessions == null ? List.of() : List.copyOf(platformSessions);
    }

    public StartBroadcastSessionResult(long sessionId, BroadcastStatus status, Instant startedAt) {
        this(sessionId, status, startedAt, List.of());
    }
}

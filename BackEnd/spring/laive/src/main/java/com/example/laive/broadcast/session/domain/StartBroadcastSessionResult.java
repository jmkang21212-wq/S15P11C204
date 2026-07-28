package com.example.laive.broadcast.session.domain;

import java.time.Instant;

public record StartBroadcastSessionResult(
        long sessionId,
        BroadcastStatus status,
        Instant startedAt
) {
}


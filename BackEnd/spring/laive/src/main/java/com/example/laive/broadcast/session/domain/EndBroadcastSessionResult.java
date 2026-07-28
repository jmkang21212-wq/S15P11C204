package com.example.laive.broadcast.session.domain;

public record EndBroadcastSessionResult(
        long sessionId,
        BroadcastStatus status,
        String reportStatus
) {
}


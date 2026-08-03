package com.example.laive.broadcast.session.dto.response;

import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;

import java.time.Instant;
import java.util.List;

public record StartBroadcastSessionResponse(
        long sessionId,
        BroadcastStatus status,
        Instant startedAt,
        List<BroadcastPlatformSessionResponse> platformSessions
) {
    public static StartBroadcastSessionResponse from(StartBroadcastSessionResult result) {
        return new StartBroadcastSessionResponse(
                result.sessionId(),
                result.status(),
                result.startedAt(),
                result.platformSessions().stream().map(BroadcastPlatformSessionResponse::from).toList()
        );
    }
}

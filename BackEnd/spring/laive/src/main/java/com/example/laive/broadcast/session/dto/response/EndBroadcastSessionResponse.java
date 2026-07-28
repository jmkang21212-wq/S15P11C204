package com.example.laive.broadcast.session.dto.response;

import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.EndBroadcastSessionResult;

public record EndBroadcastSessionResponse(
        long sessionId,
        BroadcastStatus status,
        String reportStatus
) {
    public static EndBroadcastSessionResponse from(EndBroadcastSessionResult result) {
        return new EndBroadcastSessionResponse(
                result.sessionId(),
                result.status(),
                result.reportStatus()
        );
    }
}


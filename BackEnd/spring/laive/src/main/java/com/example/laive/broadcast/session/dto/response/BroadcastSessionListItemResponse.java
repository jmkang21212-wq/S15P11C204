package com.example.laive.broadcast.session.dto.response;

import com.example.laive.broadcast.session.domain.BroadcastSessionListItem;
import com.example.laive.broadcast.session.domain.BroadcastStatus;

import java.time.Instant;

public record BroadcastSessionListItemResponse(
        long sessionId,
        String title,
        String category,
        String thumbnailUrl,
        BroadcastStatus status,
        Instant startedAt,
        Instant endedAt,
        String reportStatus
) {
    public static BroadcastSessionListItemResponse from(BroadcastSessionListItem item) {
        return new BroadcastSessionListItemResponse(
                item.sessionId(),
                item.title(),
                item.category(),
                item.thumbnailUrl(),
                item.status(),
                item.startedAt(),
                item.endedAt(),
                item.reportStatus()
        );
    }
}


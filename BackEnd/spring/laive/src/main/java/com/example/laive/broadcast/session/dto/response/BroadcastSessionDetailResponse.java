package com.example.laive.broadcast.session.dto.response;

import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.domain.BroadcastStatus;

import java.time.Instant;
import java.util.List;

public record BroadcastSessionDetailResponse(
        long sessionId,
        long platformAccountId,
        Long calendarEventId,
        String title,
        String category,
        String language,
        String thumbnailUrl,
        String vodUrl,
        BroadcastStatus status,
        Instant startedAt,
        Instant endedAt,
        List<BroadcastPlatformSessionResponse> platformSessions
) {
    public static BroadcastSessionDetailResponse from(BroadcastSessionDetail detail) {
        return from(detail, List.of());
    }

    public static BroadcastSessionDetailResponse from(
            BroadcastSessionDetail detail,
            List<com.example.laive.broadcast.session.domain.BroadcastPlatformSession> platformSessions
    ) {
        return new BroadcastSessionDetailResponse(
                detail.sessionId(),
                detail.platformAccountId(),
                detail.calendarEventId(),
                detail.title(),
                detail.category(),
                detail.language(),
                detail.thumbnailUrl(),
                detail.vodUrl(),
                detail.status(),
                detail.startedAt(),
                detail.endedAt(),
                platformSessions == null
                        ? List.of()
                        : platformSessions.stream()
                                .map(BroadcastPlatformSessionResponse::from)
                                .toList()
        );
    }
}

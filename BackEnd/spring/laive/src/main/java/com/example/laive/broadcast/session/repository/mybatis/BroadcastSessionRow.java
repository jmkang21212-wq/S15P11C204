package com.example.laive.broadcast.session.repository.mybatis;

import com.example.laive.broadcast.session.domain.BroadcastSession;
import com.example.laive.broadcast.session.domain.BroadcastStatus;

import java.time.Instant;

/**
 * MyBatis 조회 결과를 받는 영속성 전용 Row다.
 */
public record BroadcastSessionRow(
        long id,
        long streamerId,
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
        Instant createdAt,
        Instant updatedAt
) {
    public BroadcastSession toDomain() {
        return BroadcastSession.reconstitute(
                id,
                streamerId,
                platformAccountId,
                calendarEventId,
                title,
                category,
                language,
                thumbnailUrl,
                vodUrl,
                status,
                startedAt,
                endedAt,
                createdAt,
                updatedAt
        );
    }
}


package com.example.laive.broadcast.live.repository.mybatis;

import java.time.Instant;

/**
 * {@code live_briefings} 한 행이다. map-underscore-to-camel-case로 바인딩된다.
 */
public record LiveBriefingRow(
        long briefingId,
        String briefingType,
        String message,
        Long promiseId,
        Instant createdAt
) {
}

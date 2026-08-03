package com.example.laive.broadcast.session.integration;

import java.time.Duration;
import java.time.Instant;

/**
 * 치지직 채팅 수집기의 시작·종료 경계를 정의한다. 구현은 sessionId 기준으로 멱등해야 한다.
 */
public interface ChatCollectorGateway {

    default void start(long sessionId, long streamerId, long platformAccountId) {
    }

    /** 신규 Collector는 플랫폼 자식 세션을 worker 식별자로 사용한다. */
    default void startPlatformSession(
            long sessionId,
            long platformSessionId,
            long streamerId,
            long platformAccountId,
            String platformCode
    ) {
        start(sessionId, streamerId, platformAccountId);
    }

    default void stopAndFlush(long sessionId, Instant endedAt, Duration gracePeriod) {
    }

    default void stopPlatformSession(
            long platformSessionId,
            Instant endedAt,
            Duration gracePeriod
    ) {
    }
}

package com.example.laive.broadcast.session.integration;

import java.time.Duration;
import java.time.Instant;

/**
 * STT 버퍼와 실시간 AI 분석 스케줄러의 세션 생명주기 Port다.
 */
public interface AiSessionGateway {

    default void start(long sessionId, String language) {
    }

    default void stopAndFlush(long sessionId, Instant endedAt, Duration gracePeriod) {
    }
}


package com.example.laive.broadcast.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 방송 세션 Lock, Outbox, 종료 정리와 FastAPI 연동 설정이다.
 */
@ConfigurationProperties(prefix = "laive.broadcast-session")
public record BroadcastSessionProperties(
        Duration lifecycleLockTtl,
        Duration finalizationGracePeriod,
        Duration streamTtlAfterEnd,
        Outbox outbox,
        Ai ai
) {
    public record Outbox(
            long pollDelayMs,
            int batchSize,
            Duration lease,
            int maxAttempts,
            Duration maxBackoff
    ) {
    }

    public record Ai(
            boolean reportEnabled,
            String baseUrl,
            String serviceToken
    ) {
    }
}

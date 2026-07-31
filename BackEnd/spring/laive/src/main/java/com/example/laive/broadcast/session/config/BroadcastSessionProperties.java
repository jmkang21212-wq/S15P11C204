package com.example.laive.broadcast.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 방송 세션 Lock, Outbox, 종료 정리, 음성 AI 접속 티켓 설정이다.
 */
@ConfigurationProperties(prefix = "laive.broadcast-session")
public record BroadcastSessionProperties(
        Duration lifecycleLockTtl,
        Duration finalizationGracePeriod,
        Duration streamTtlAfterEnd,
        /**
         * 음성 AI WebSocket 접속 티켓의 유효 시간. 발급 직후 곧바로 쓰이고
         * 재연결마다 새로 받으므로 짧게 잡는다.
         */
        Duration voiceTicketTtl,
        Outbox outbox
) {
    public record Outbox(
            long pollDelayMs,
            int batchSize,
            Duration lease,
            int maxAttempts,
            Duration maxBackoff
    ) {
    }
}

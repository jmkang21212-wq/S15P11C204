package com.example.laive.broadcast.session.integration;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 실시간 콘솔의 세션 상태와 WebSocket 재연결용 Redis Stream을 관리한다.
 */
@Component
public class SessionRedisGateway {

    private static final long STREAM_MAX_LENGTH = 1_000L;

    private final StringRedisTemplate redisTemplate;
    private final BroadcastSessionProperties properties;

    public SessionRedisGateway(
            StringRedisTemplate redisTemplate,
            BroadcastSessionProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void initialize(long sessionId, long streamerId, Instant startedAt) {
        redisTemplate.opsForHash().putAll(stateKey(sessionId), Map.of(
                "sessionId", Long.toString(sessionId),
                "streamerId", Long.toString(streamerId),
                "status", BroadcastStatus.PREPARING.name(),
                "startedAt", startedAt.toString()
        ));
    }

    public void publishStatus(long sessionId, BroadcastStatus status, Instant occurredAt) {
        redisTemplate.opsForHash().put(stateKey(sessionId), "status", status.name());
        String key = streamKey(sessionId);
        redisTemplate.opsForStream().add(key, Map.of(
                "type", "SESSION_STATUS_CHANGED",
                "sessionId", Long.toString(sessionId),
                "status", status.name(),
                "occurredAt", occurredAt.toString()
        ));
        redisTemplate.opsForStream().trim(key, STREAM_MAX_LENGTH);
    }

    public void finish(long sessionId, Instant endedAt) {
        redisTemplate.opsForHash().putAll(stateKey(sessionId), Map.of(
                "status", BroadcastStatus.ENDED.name(),
                "endedAt", endedAt.toString()
        ));
        publishStatus(sessionId, BroadcastStatus.ENDED, endedAt);
        long ttlMillis = properties.streamTtlAfterEnd().toMillis();
        redisTemplate.expire(stateKey(sessionId), ttlMillis, TimeUnit.MILLISECONDS);
        redisTemplate.expire(streamKey(sessionId), ttlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 세션 상태 Hash의 키다. 수집기 상태도 같은 Hash에 붙어 실시간 콘솔이 한 번에 읽는다(명세 10.6).
     * 키 문자열이 두 곳에 복제되지 않도록 여기서만 만든다.
     */
    public static String stateKey(long sessionId) {
        return "session:" + sessionId + ":state";
    }

    private String streamKey(long sessionId) {
        return "ws:session:" + sessionId + ":events";
    }
}


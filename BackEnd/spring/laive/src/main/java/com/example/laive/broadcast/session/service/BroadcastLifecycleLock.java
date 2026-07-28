package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 같은 스트리머의 시작·종료 요청을 짧게 직렬화하는 Redis Lock이다.
 *
 * Redis 장애 시 DB Partial Unique와 행 잠금이 최종 정합성을 보장하므로 작업을 계속한다.
 */
@Component
public class BroadcastLifecycleLock {

    private static final Logger log = LoggerFactory.getLogger(BroadcastLifecycleLock.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final BroadcastSessionProperties properties;

    public BroadcastLifecycleLock(
            StringRedisTemplate redisTemplate,
            BroadcastSessionProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public <T> T execute(
            long streamerId,
            Supplier<? extends RuntimeException> contentionException,
            Supplier<T> action
    ) {
        String key = "lock:broadcast-session:lifecycle:" + streamerId;
        String token = UUID.randomUUID().toString();
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    key,
                    token,
                    properties.lifecycleLockTtl()
            ));
        } catch (DataAccessException exception) {
            log.warn("Redis 방송 lifecycle lock을 사용할 수 없어 DB 보호 장치로 진행합니다.");
            return action.get();
        }

        if (!acquired) {
            throw contentionException.get();
        }

        try {
            return action.get();
        } finally {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
            } catch (DataAccessException exception) {
                log.warn("Redis 방송 lifecycle lock 해제에 실패했습니다. TTL로 자동 해제됩니다.");
            }
        }
    }
}


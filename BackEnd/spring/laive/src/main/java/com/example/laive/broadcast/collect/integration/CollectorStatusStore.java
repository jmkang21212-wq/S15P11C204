package com.example.laive.broadcast.collect.integration;

import com.example.laive.broadcast.collect.config.CollectorProperties;
import com.example.laive.broadcast.collect.domain.CollectorState;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 수집기 연결 상태를 세션 상태 Hash에 기록하고 읽는다(명세 10.6 {@code services.collector}).
 *
 * <p>DB가 아니라 Redis에 두는 이유: 방송 중에만 의미가 있는 현재값이고 초 단위로
 * 갱신된다. 종료 후 남겨야 할 것은 상태가 아니라 공백 구간이며 그건
 * {@code collection_gaps}에 영구 저장된다(V9).
 */
@Component
public class CollectorStatusStore {

    private static final String FIELD_STATE = "collectorStatus";
    private static final String FIELD_REPORTED_AT = "collectorStatusAt";

    private static final Logger log = LoggerFactory.getLogger(CollectorStatusStore.class);

    private final StringRedisTemplate redisTemplate;
    private final CollectorProperties properties;
    private final Clock clock;

    public CollectorStatusStore(
            StringRedisTemplate redisTemplate,
            CollectorProperties properties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 수집기가 보고한 상태를 기록한다.
     *
     * <p>세션 상태 Hash가 없으면 무시한다. 시작 전이거나 이미 정리된 세션에 대한
     * 뒤늦은 보고이며, 그대로 쓰면 TTL 없는 키가 남아 쌓인다. 상태 Hash는
     * 준비 이벤트가 수집기를 띄우기 전에 먼저 만들어지므로(BroadcastPreparation-
     * RequestedHandler) 정상 흐름에서는 항상 존재한다.
     */
    public void record(long sessionId, CollectorState state, Instant reportedAt) {
        String key = SessionRedisGateway.stateKey(sessionId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.debug("세션 상태가 없어 수집기 상태 보고를 무시합니다: sessionId={} state={}",
                    sessionId, state);
            return;
        }
        redisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_STATE, state.name(),
                FIELD_REPORTED_AT, reportedAt.toString()
        ));
    }

    /**
     * 현재 수집기 상태를 반환한다.
     *
     * <p>보고가 끊긴 지 heartbeatTimeout을 넘겼으면 마지막 값이 RUNNING이어도
     * STOPPED로 내린다. 수집기 프로세스가 죽으면 마지막 상태를 갱신할 주체가
     * 사라지므로, 저장된 값을 그대로 믿으면 화면이 영원히 "정상 수집 중"을
     * 보여준다. 수집이 멈춘 것을 알리는 것이 이 필드의 존재 이유다.
     */
    public CollectorState read(long sessionId) {
        String key = SessionRedisGateway.stateKey(sessionId);
        Object state = redisTemplate.opsForHash().get(key, FIELD_STATE);
        Object reportedAt = redisTemplate.opsForHash().get(key, FIELD_REPORTED_AT);
        if (state == null || reportedAt == null) {
            return CollectorState.STOPPED;
        }

        Instant lastReport;
        try {
            lastReport = Instant.parse(reportedAt.toString());
        } catch (DateTimeParseException exception) {
            log.warn("수집기 상태 보고 시각을 읽을 수 없습니다: sessionId={} value={}",
                    sessionId, reportedAt);
            return CollectorState.STOPPED;
        }
        if (lastReport.plus(properties.heartbeatTimeout()).isBefore(clock.instant())) {
            return CollectorState.STOPPED;
        }

        try {
            return CollectorState.valueOf(state.toString());
        } catch (IllegalArgumentException exception) {
            log.warn("알 수 없는 수집기 상태입니다: sessionId={} value={}", sessionId, state);
            return CollectorState.STOPPED;
        }
    }
}

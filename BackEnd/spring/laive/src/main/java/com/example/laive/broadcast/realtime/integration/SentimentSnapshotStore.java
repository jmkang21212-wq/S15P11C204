package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.realtime.domain.SentimentWindow;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 실시간 콘솔이 폴링으로 읽는 현재값을 세션 상태 Hash에 남긴다(명세 10.6).
 *
 * <p>DB에 저장하지 않는 이유는 {@code CollectorStatusStore}와 같다 — 방송 중에만
 * 의미 있는 현재값이고, 세션·구간 단위 확정본은 REPORTS와 TRIGGERS가 따로 갖는다.
 *
 * <p>sentiment는 명세 10.6의 sentiment 객체 그대로를 JSON 문자열 한 필드로 둔다.
 * 필드 여러 개로 쪼개면 갱신 도중 읽기가 찢어진 값을 볼 수 있고, 한 필드면
 * HSET 한 번으로 원자 교체된다.
 */
@Component
public class SentimentSnapshotStore {

    private static final String FIELD_SENTIMENT = "sentiment";
    private static final String FIELD_SENTIMENT_AT = "sentimentAt";
    private static final String FIELD_CURRENT_VIEWERS = "currentViewers";
    private static final String FIELD_VIEWERS_AT = "viewersAt";

    private static final Logger log = LoggerFactory.getLogger(SentimentSnapshotStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SentimentSnapshotStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 최신 윈도우 지표를 기록한다.
     *
     * <p>세션 상태 Hash가 없으면 무시한다 — 이미 정리된 세션에 대한 뒤늦은 기록이며,
     * 그대로 쓰면 TTL 없는 키가 남는다({@code CollectorStatusStore.record}와 같은 판단).
     */
    public void recordSentiment(long sessionId, SentimentWindow window, Instant recordedAt) {
        String key = SessionRedisGateway.stateKey(sessionId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.debug("세션 상태가 없어 여론 스냅샷을 무시합니다: sessionId={}", sessionId);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(window);
        } catch (JsonProcessingException exception) {
            log.warn("여론 스냅샷을 직렬화할 수 없습니다: sessionId={}", sessionId, exception);
            return;
        }
        redisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_SENTIMENT, json,
                FIELD_SENTIMENT_AT, recordedAt.toString()
        ));
    }

    /** 최신 시청자 수를 기록한다. 세션 상태 Hash가 없으면 무시한다. */
    public void recordViewers(long sessionId, int viewerCount, Instant measuredAt) {
        String key = SessionRedisGateway.stateKey(sessionId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.debug("세션 상태가 없어 시청자 수 기록을 무시합니다: sessionId={}", sessionId);
            return;
        }
        redisTemplate.opsForHash().putAll(key, Map.of(
                FIELD_CURRENT_VIEWERS, Integer.toString(viewerCount),
                FIELD_VIEWERS_AT, measuredAt.toString()
        ));
    }

    /** 저장된 최신 윈도우 지표를 읽는다. 없거나 읽을 수 없으면 비어 있다. */
    public Optional<SentimentWindow> readSentiment(long sessionId) {
        Object json = redisTemplate.opsForHash()
                .get(SessionRedisGateway.stateKey(sessionId), FIELD_SENTIMENT);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json.toString(), SentimentWindow.class));
        } catch (JsonProcessingException exception) {
            log.warn("여론 스냅샷을 읽을 수 없습니다: sessionId={}", sessionId, exception);
            return Optional.empty();
        }
    }

    /** 여론 스냅샷이 기록된 시각을 읽는다. 콘솔이 AI 파이프라인의 생사 판단에 쓴다. */
    public Optional<Instant> readSentimentRecordedAt(long sessionId) {
        Object value = redisTemplate.opsForHash()
                .get(SessionRedisGateway.stateKey(sessionId), FIELD_SENTIMENT_AT);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.toString()));
        } catch (java.time.format.DateTimeParseException exception) {
            log.warn("여론 스냅샷 시각을 읽을 수 없습니다: sessionId={} value={}", sessionId, value);
            return Optional.empty();
        }
    }

    /** 저장된 최신 시청자 수를 읽는다. 없거나 숫자가 아니면 비어 있다. */
    public Optional<Integer> readViewers(long sessionId) {
        Object value = redisTemplate.opsForHash()
                .get(SessionRedisGateway.stateKey(sessionId), FIELD_CURRENT_VIEWERS);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.toString()));
        } catch (NumberFormatException exception) {
            log.warn("시청자 수 기록을 읽을 수 없습니다: sessionId={} value={}", sessionId, value);
            return Optional.empty();
        }
    }
}

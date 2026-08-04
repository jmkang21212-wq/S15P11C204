package com.example.laive.broadcast.collect.integration;

import com.example.laive.broadcast.collect.domain.CollectorState;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실시간 콘솔의 services.collector(명세 10.6)가 읽을 값을 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.collector.heartbeat-timeout=30s",
        "spring.task.scheduling.enabled=false"
})
class CollectorStatusStoreTest {

    @Autowired
    private CollectorStatusStore store;

    @Autowired
    private SessionRedisGateway redisGateway;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final long sessionId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(SessionRedisGateway.stateKey(sessionId));
    }

    @Test
    void reportsStoppedWhenNothingRecorded() {
        assertThat(store.read(sessionId)).isEqualTo(CollectorState.STOPPED);
    }

    @Test
    void readsBackFreshReport() {
        givenLiveSession();

        store.record(sessionId, CollectorState.RUNNING, Instant.now());

        assertThat(store.read(sessionId)).isEqualTo(CollectorState.RUNNING);
    }

    @Test
    void readsBackReconnecting() {
        givenLiveSession();

        store.record(sessionId, CollectorState.RECONNECTING, Instant.now());

        assertThat(store.read(sessionId)).isEqualTo(CollectorState.RECONNECTING);
    }

    @Test
    void downgradesStaleRunningToStopped() {
        // 수집기 프로세스가 죽으면 마지막 상태를 갱신할 주체가 사라진다.
        // 저장값을 그대로 믿으면 화면이 영원히 "정상 수집 중"을 보여준다.
        givenLiveSession();

        store.record(sessionId, CollectorState.RUNNING, Instant.now().minusSeconds(120));

        assertThat(store.read(sessionId)).isEqualTo(CollectorState.STOPPED);
    }

    @Test
    void ignoresReportForUnknownSession() {
        // 시작 전이거나 이미 정리된 세션에 대한 뒤늦은 보고다.
        // 받아주면 TTL 없는 키가 남아 쌓인다.
        store.record(sessionId, CollectorState.RUNNING, Instant.now());

        assertThat(redisTemplate.hasKey(SessionRedisGateway.stateKey(sessionId))).isFalse();
        assertThat(store.read(sessionId)).isEqualTo(CollectorState.STOPPED);
    }

    /** 수집기 상태는 준비 이벤트가 만든 세션 상태 Hash 위에 붙는다. */
    private void givenLiveSession() {
        redisGateway.initialize(sessionId, 1L, Instant.now());
        redisTemplate.opsForHash().put(
                SessionRedisGateway.stateKey(sessionId), "status", BroadcastStatus.LIVE.name());
    }
}

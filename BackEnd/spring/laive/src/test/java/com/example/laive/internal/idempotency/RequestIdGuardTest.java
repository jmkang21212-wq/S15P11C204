package com.example.laive.internal.idempotency;

import com.example.laive.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * X-Request-Id 멱등 처리(명세 13.1)를 실제 Redis에서 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
class RequestIdGuardTest {

    @Autowired
    private RequestIdGuard guard;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String requestId = "test-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        redisTemplate.delete("internal:request-id:" + requestId);
    }

    @Test
    void allowsFirstRequest() {
        assertThat(guard.begin(requestId)).isEmpty();
    }

    @Test
    void returnsRecordedResponseOnRetry() {
        guard.begin(requestId);
        guard.complete(requestId, "{\"acceptedCount\":42}");

        Optional<String> retry = guard.begin(requestId);

        assertThat(retry).contains("{\"acceptedCount\":42}");
    }

    @Test
    void rejectsRetryWhileFirstAttemptIsStillRunning() {
        // 저장 여부가 확정되지 않은 시점이라 어떤 응답을 줘도 거짓말이 될 수 있다.
        guard.begin(requestId);

        assertThatThrownBy(() -> guard.begin(requestId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INTERNAL_REQUEST_IN_PROGRESS");
    }

    @Test
    void allowsRetryAfterAbandon() {
        // 일시적 실패가 영구 실패가 되면 안 된다. 실패한 선점은 되돌려야
        // 같은 requestId의 재시도가 통과한다.
        guard.begin(requestId);
        guard.abandon(requestId);

        assertThat(guard.begin(requestId)).isEmpty();
    }

    @Test
    void treatsDifferentRequestIdsIndependently() {
        String other = "test-" + UUID.randomUUID();
        guard.begin(requestId);
        try {
            assertThat(guard.begin(other)).isEmpty();
        } finally {
            redisTemplate.delete("internal:request-id:" + other);
        }
    }
}

package com.example.laive.broadcast.session;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.broadcast.session.repository.BroadcastSessionRepository;
import com.example.laive.broadcast.session.service.BroadcastSessionCommandService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis lifecycle lock과 PostgreSQL 활성 세션 Partial Unique가 동시 시작을 막는지 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.broadcast-session.outbox.poll-delay-ms=600000"
})
class BroadcastSessionConcurrencyIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 20;

    @Autowired
    private BroadcastSessionCommandService commandService;

    @Autowired
    private BroadcastSessionRepository repository;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long createdStreamerId;

    @AfterEach
    void deleteCreatedTestData() {
        if (createdStreamerId != null) {
            // 이 테스트가 생성한 스트리머 한 명만 삭제하며 연관 테스트 데이터는 FK로 정리된다.
            jdbcTemplate.update("DELETE FROM streamers WHERE id = ?", createdStreamerId);
        }
    }

    @Test
    void allowsOnlyOneActiveSessionWhenStartRequestsArriveTogether() throws Exception {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);

        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
                int requestNumber = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        return "START_TIMEOUT";
                    }
                    try {
                        commandService.start(
                                streamerId,
                                new StartBroadcastSessionCommand(
                                        platformAccountId,
                                        null,
                                        "동시 방송 시작 " + requestNumber,
                                        "FPS",
                                        "ko"
                                )
                        );
                        return "SUCCESS";
                    } catch (BroadcastSessionException exception) {
                        return exception.getCode();
                    }
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn("SUCCESS"::equals).hasSize(1);
            assertThat(results).filteredOn("SESSION_ALREADY_LIVE"::equals)
                    .hasSize(CONCURRENT_REQUESTS - 1);
            assertThat(repository.findCurrent(streamerId)).isPresent();
            assertThat(activeSessionCount(streamerId)).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null,
                "동시성 테스트 " + UUID.randomUUID(),
                null
        );
        authMapper.insertStreamer(insert);
        createdStreamerId = insert.getId();
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "concurrency-test-" + UUID.randomUUID(),
                "동시성 테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }

    private int activeSessionCount(long streamerId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM broadcast_sessions
                 WHERE streamer_id = ?
                   AND status IN ('PREPARING', 'LIVE')
                """,
                Integer.class,
                streamerId
        );
        return count == null ? 0 : count;
    }
}

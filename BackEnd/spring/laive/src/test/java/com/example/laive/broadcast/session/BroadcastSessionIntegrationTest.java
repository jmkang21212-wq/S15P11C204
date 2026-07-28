package com.example.laive.broadcast.session;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.broadcast.session.repository.BroadcastSessionRepository;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 스키마에서 ERD FK, Partial Unique, 리포트와 Outbox 원자성을 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class BroadcastSessionIntegrationTest {

    @Autowired
    private BroadcastSessionTransactionService transactionService;

    @Autowired
    private BroadcastSessionRepository repository;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsCompleteLifecycleUsingErdRelationships() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);

        var started = transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId,
                        null,
                        "ERD 통합 테스트 방송",
                        "FPS",
                        "ko"
                )
        );

        assertThat(started.status()).isEqualTo(BroadcastStatus.PREPARING);
        assertThat(repository.findCurrent(streamerId)).isPresent();
        assertThat(outboxCount(started.sessionId())).isEqualTo(1);

        transactionService.activate(started.sessionId());
        assertThat(repository.findDetail(streamerId, started.sessionId()))
                .get()
                .extracting(detail -> detail.status())
                .isEqualTo(BroadcastStatus.LIVE);
        assertThat(outboxCount(started.sessionId())).isEqualTo(2);

        assertThatThrownBy(() -> transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId,
                        null,
                        "중복 방송",
                        null,
                        "ko"
                )
        ))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("SESSION_ALREADY_LIVE");

        var ended = transactionService.end(streamerId, started.sessionId());
        assertThat(ended.status()).isEqualTo(BroadcastStatus.ENDED);
        assertThat(ended.reportStatus()).isEqualTo("PROCESSING");
        assertThat(repository.findCurrent(streamerId)).isEmpty();
        assertThat(repository.findDetail(streamerId, started.sessionId()))
                .get()
                .satisfies(detail -> {
                    assertThat(detail.status()).isEqualTo(BroadcastStatus.ENDED);
                    assertThat(detail.endedAt()).isNotNull();
                });
        assertThat(reportCount(started.sessionId())).isEqualTo(1);
        assertThat(outboxCount(started.sessionId())).isEqualTo(3);
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null,
                "방송 세션 테스트 " + UUID.randomUUID(),
                null
        );
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "session-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }

    private int outboxCount(long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM broadcast_session_outbox WHERE aggregate_id = ?",
                Integer.class,
                sessionId
        );
        return count == null ? 0 : count;
    }

    private int reportCount(long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reports WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        return count == null ? 0 : count;
    }
}


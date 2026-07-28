package com.example.laive.broadcast.session;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.SessionStatistics;
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

    /** 리포트 감정 통계는 chat_messages의 건별 라벨을 세어 확정한다. */
    @Test
    void aggregatesSentimentCountsByLabel() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        long sessionId = createPreparingSession(streamerId, platformAccountId);
        long viewerId = createViewer(platformAccountId);

        insertViewerMetric(sessionId, 1000);
        insertViewerMetric(sessionId, 3000);
        insertChat(sessionId, viewerId, "JOY");
        insertChat(sessionId, viewerId, "JOY");
        insertChat(sessionId, viewerId, "EXCITEMENT");
        insertChat(sessionId, viewerId, "ANGER");

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.averageViewers()).isEqualTo(2000);
        assertThat(statistics.peakViewers()).isEqualTo(3000);
        assertThat(statistics.totalChat()).isEqualTo(4);
        assertThat(statistics.analyzedChat()).isEqualTo(4);
        assertThat(statistics.joyCount()).isEqualTo(2);
        assertThat(statistics.excitementCount()).isEqualTo(1);
        assertThat(statistics.angerCount()).isEqualTo(1);
        assertThat(statistics.neutralCount()).isZero();
    }

    /**
     * 분류 호출이 실패한 채팅은 라벨 없이 저장된다.
     * total_chat에는 들어가고 analyzed_chat에서는 빠져야 분류 커버리지를 알 수 있다.
     */
    @Test
    void excludesUnlabeledChatsFromAnalyzedChat() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        long sessionId = createPreparingSession(streamerId, platformAccountId);
        long viewerId = createViewer(platformAccountId);

        insertChat(sessionId, viewerId, "JOY");
        insertChat(sessionId, viewerId, null);
        insertChat(sessionId, viewerId, null);

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.totalChat()).isEqualTo(3);
        assertThat(statistics.analyzedChat()).isEqualTo(1);
        assertThat(statistics.joyCount()).isEqualTo(1);
    }

    /** 채팅이 없는 세션은 건수가 모두 0이고 시청자 지표만 NULL이다 (명세 1.5). */
    @Test
    void returnsZeroSentimentCountsWhenSessionHasNoChat() {
        long streamerId = createStreamer();
        long sessionId = createPreparingSession(streamerId, createPlatformAccount(streamerId));

        insertViewerMetric(sessionId, 1500);

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.averageViewers()).isEqualTo(1500);
        assertThat(statistics.totalChat()).isZero();
        assertThat(statistics.analyzedChat()).isZero();
        assertThat(statistics.joyCount()).isZero();
    }

    /** 시청자 지표가 한 건도 없으면 평균·최대만 NULL이다. */
    @Test
    void leavesViewerMetricsNullWhenSessionHasNoMetric() {
        long streamerId = createStreamer();
        long sessionId = createPreparingSession(streamerId, createPlatformAccount(streamerId));

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.averageViewers()).isNull();
        assertThat(statistics.peakViewers()).isNull();
        assertThat(statistics.totalChat()).isZero();
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

    /** 통계 집계는 세션 상태와 무관하게 원천 테이블만 읽으므로 PREPARING 세션으로 충분하다. */
    private long createPreparingSession(long streamerId, long platformAccountId) {
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId,
                        null,
                        "통계 집계 테스트 방송",
                        null,
                        "ko"
                )
        ).sessionId();
    }

    private long createViewer(long platformAccountId) {
        Long viewerId = jdbcTemplate.queryForObject(
                """
                INSERT INTO viewer_profiles (platform_account_id, viewer_hash)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                platformAccountId,
                "sha256:" + UUID.randomUUID()
        );
        return viewerId;
    }

    private void insertViewerMetric(long sessionId, int viewerCount) {
        jdbcTemplate.update(
                """
                INSERT INTO viewer_metrics (session_id, measured_at, viewer_count)
                VALUES (?, clock_timestamp(), ?)
                """,
                sessionId,
                viewerCount
        );
    }

    /** sentimentLabel이 null이면 분류 호출이 실패해 라벨 없이 저장된 채팅이다. */
    private void insertChat(long sessionId, long viewerId, String sentimentLabel) {
        jdbcTemplate.update(
                """
                INSERT INTO chat_messages (
                    session_id, viewer_id, message, sent_at, sentiment_label
                ) VALUES (?, ?, ?, clock_timestamp(), ?::varchar)
                """,
                sessionId,
                viewerId,
                "통계 집계 테스트 채팅",
                sentimentLabel
        );
    }
}


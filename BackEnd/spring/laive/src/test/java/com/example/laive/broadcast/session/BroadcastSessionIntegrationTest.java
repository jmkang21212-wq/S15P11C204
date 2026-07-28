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

import java.math.BigDecimal;
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

    /**
     * 리포트 감정 비율은 윈도우별 채팅량 가중 평균이다.
     * 단순 평균이면 긍정 0.500이 되는 데이터로 가중치가 실제로 걸리는지 확인한다.
     */
    @Test
    void weightsSentimentRatiosByWindowChatVolume() {
        long streamerId = createStreamer();
        long sessionId = createPreparingSession(streamerId);

        insertViewerMetric(sessionId, 1000);
        insertViewerMetric(sessionId, 3000);
        // 한산한 윈도우 10건, 피크 윈도우 990건
        insertSentimentSnapshot(sessionId, "0.900", "0.100", "0.000", 10);
        insertSentimentSnapshot(sessionId, "0.100", "0.200", "0.700", 990);

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.averageViewers()).isEqualTo(2000);
        assertThat(statistics.peakViewers()).isEqualTo(3000);
        assertThat(statistics.totalChat()).isZero();
        assertThat(statistics.positiveRatio()).isEqualByComparingTo("0.108");
        assertThat(statistics.neutralRatio()).isEqualByComparingTo("0.199");
        assertThat(statistics.negativeRatio()).isEqualByComparingTo("0.693");
    }

    /** message_count가 없는 과거 스냅샷만 있으면 단순 평균으로 되돌아간다. */
    @Test
    void fallsBackToSimpleAverageWhenWindowChatVolumeIsUnknown() {
        long streamerId = createStreamer();
        long sessionId = createPreparingSession(streamerId);

        insertSentimentSnapshot(sessionId, "0.900", "0.100", "0.000", null);
        insertSentimentSnapshot(sessionId, "0.100", "0.200", "0.700", null);

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.positiveRatio()).isEqualByComparingTo("0.500");
        assertThat(statistics.neutralRatio()).isEqualByComparingTo("0.150");
        assertThat(statistics.negativeRatio()).isEqualByComparingTo("0.350");
    }

    /** 감정 분석 결과가 없으면 세 비율은 NULL로 남는다 (명세 1.5). */
    @Test
    void leavesSentimentRatiosNullWhenNoSnapshotExists() {
        long streamerId = createStreamer();
        long sessionId = createPreparingSession(streamerId);

        insertViewerMetric(sessionId, 1500);

        SessionStatistics statistics = repository.calculateStatistics(sessionId);

        assertThat(statistics.averageViewers()).isEqualTo(1500);
        assertThat(statistics.positiveRatio()).isNull();
        assertThat(statistics.neutralRatio()).isNull();
        assertThat(statistics.negativeRatio()).isNull();
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
    private long createPreparingSession(long streamerId) {
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        createPlatformAccount(streamerId),
                        null,
                        "통계 집계 테스트 방송",
                        null,
                        "ko"
                )
        ).sessionId();
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

    private void insertSentimentSnapshot(
            long sessionId,
            String positiveRatio,
            String neutralRatio,
            String negativeRatio,
            Integer messageCount
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO sentiment_snapshots (
                    session_id, positive_ratio, neutral_ratio, negative_ratio,
                    message_count, captured_at
                ) VALUES (?, ?, ?, ?, ?::int, clock_timestamp())
                """,
                sessionId,
                new BigDecimal(positiveRatio),
                new BigDecimal(neutralRatio),
                new BigDecimal(negativeRatio),
                messageCount
        );
    }
}


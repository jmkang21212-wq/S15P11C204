package com.example.laive.growth;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;
import com.example.laive.growth.domain.GrowthPeriod;
import com.example.laive.growth.domain.GrowthPeriodType;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class GrowthMapperIntegrationTest {
    private static final LocalDate WEEK_START = LocalDate.parse("2026-07-13");
    private static final LocalDate WEEK_END = LocalDate.parse("2026-07-19");

    @Autowired
    private GrowthReportQueryRepository repository;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private PlatformAccountMapper platformAccountMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void emptyBucketAndEmptyGoalHaveZeroBroadcastMinutes() {
        long streamerId = createStreamer();

        var series = repository.findSeries(
                streamerId,
                GrowthPeriodType.WEEKLY,
                new GrowthPeriod(WEEK_START, WEEK_END)
        );
        assertThat(series).singleElement().satisfies(point -> {
            assertThat(point.averageViewers()).isNull();
            assertThat(point.totalChat()).isZero();
            assertThat(point.broadcastMinutes()).isZero();
        });

        long goalId = jdbcTemplate.queryForObject(
                """
                INSERT INTO growth_goals
                    (streamer_id, metric_type, target_value, unit,
                     period_start, period_end, status)
                VALUES (?, ?, 600, ?, ?, ?, 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                streamerId,
                GrowthMetricType.BROADCAST_MINUTES.name(),
                GrowthMetricUnit.MINUTES.name(),
                Date.valueOf(WEEK_START),
                Date.valueOf(WEEK_END)
        );

        assertThat(repository.findGoalMetrics(streamerId, List.of(goalId)))
                .singleElement()
                .satisfies(metrics -> {
                    assertThat(metrics.broadcastMinutes()).isEqualByComparingTo("0.00");
                    assertThat(metrics.broadcastsPerWeek()).isEqualByComparingTo("0.00");
                });
    }

    @Test
    void splitsBoundarySessionsAndUsesDurationWeightedAverage() {
        long streamerId = createStreamer();
        long platformId = createPlatform(streamerId);

        long boundarySession = createEndedSession(
                streamerId,
                platformId,
                Instant.parse("2026-07-12T23:30:00Z"),
                Instant.parse("2026-07-13T00:30:00Z")
        );
        createReport(boundarySession, "COMPLETED", 100, 100);

        long currentSession = createEndedSession(
                streamerId,
                platformId,
                Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-13T01:30:00Z")
        );
        createReport(currentSession, "PARTIAL", 200, 300);

        long processingSession = createEndedSession(
                streamerId,
                platformId,
                Instant.parse("2026-07-14T00:00:00Z"),
                Instant.parse("2026-07-14T10:00:00Z")
        );
        createReport(processingSession, "PROCESSING", 9999, 9999);

        long otherStreamerId = createStreamer();
        long otherPlatformId = createPlatform(otherStreamerId);
        long otherSession = createEndedSession(
                otherStreamerId,
                otherPlatformId,
                Instant.parse("2026-07-15T00:00:00Z"),
                Instant.parse("2026-07-15T10:00:00Z")
        );
        createReport(otherSession, "COMPLETED", 9999, 9999);

        var point = repository.findSeries(
                streamerId,
                GrowthPeriodType.WEEKLY,
                new GrowthPeriod(WEEK_START, WEEK_END)
        ).getFirst();

        assertThat(point.averageViewers()).isEqualTo(175);
        assertThat(point.broadcastMinutes()).isEqualTo(120);
        assertThat(point.totalChat()).isEqualTo(300);
    }

    private long createStreamer() {
        StreamerInsert streamer = new StreamerInsert(
                "growth-" + UUID.randomUUID() + "@example.com", "growth tester", null
        );
        authMapper.insertStreamer(streamer);
        return streamer.getId();
    }

    private long createPlatform(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "growth-" + UUID.randomUUID(),
                "growth channel",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }

    private long createEndedSession(
            long streamerId,
            long platformId,
            Instant startedAt,
            Instant endedAt
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, category, language,
                     status, started_at, ended_at)
                VALUES (?, ?, 'growth broadcast', 'GAME', 'ko', 'ENDED', ?, ?)
                RETURNING id
                """,
                Long.class,
                streamerId,
                platformId,
                Timestamp.from(startedAt),
                Timestamp.from(endedAt)
        );
    }

    private void createReport(
            long sessionId,
            String status,
            int avgViewers,
            long totalChat
    ) {
        if ("COMPLETED".equals(status) || "PARTIAL".equals(status)) {
            jdbcTemplate.update(
                    """
                    INSERT INTO reports
                        (session_id, status, avg_viewers, peak_viewers, total_chat, generated_at)
                    VALUES (?, ?, ?, ?, ?, now())
                    """,
                    sessionId, status, avgViewers, avgViewers, totalChat
            );
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO reports
                    (session_id, status, avg_viewers, peak_viewers, total_chat)
                VALUES (?, ?, ?, ?, ?)
                """,
                sessionId, status, avgViewers, avgViewers, totalChat
        );
    }
}

package com.example.laive.dashboard;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.dashboard.repository.DashboardQueryRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class DashboardMapperIntegrationTest {
    @Autowired
    private DashboardQueryRepository repository;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private PlatformAccountMapper platformAccountMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void readsDashboardCardsAndAggregatesCompletedWeeks() {
        long streamerId = createStreamer();
        long platformId = createPlatform(streamerId);
        Instant previousStart = Instant.parse("2026-07-13T00:00:00Z");
        Instant currentStart = Instant.parse("2026-07-20T00:00:00Z");
        Instant currentEnd = Instant.parse("2026-07-27T00:00:00Z");

        long previousSessionId = createEndedSession(
                streamerId, platformId, "previous", previousStart.plus(1, ChronoUnit.DAYS), 60
        );
        createReport(previousSessionId, 100, 180, 1000, "previous summary");

        long currentSessionId = createEndedSession(
                streamerId, platformId, "current", currentStart.plus(2, ChronoUnit.DAYS), 120
        );
        long reportId = createReport(currentSessionId, 200, 300, 1500, "current summary");

        Instant upcomingStart = Instant.parse("2026-08-01T12:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO calendar_events
                    (streamer_id, platform_account_id, event_type, title, start_at, end_at, status)
                VALUES (?, ?, 'BROADCAST', 'next broadcast', ?, ?, 'SCHEDULED')
                """,
                streamerId, platformId, Timestamp.from(upcomingStart),
                Timestamp.from(upcomingStart.plus(2, ChronoUnit.HOURS))
        );

        long transcriptId = jdbcTemplate.queryForObject(
                """
                INSERT INTO transcripts (session_id, start_ms, end_ms, transcript)
                VALUES (?, 60000, 90000, 'promise source')
                RETURNING id
                """,
                Long.class, currentSessionId
        );
        insertPromise(streamerId, currentSessionId, transcriptId, "first", "PENDING",
                LocalDate.of(2026, 8, 1));
        insertPromise(streamerId, currentSessionId, transcriptId, "second", "ON_HOLD",
                LocalDate.of(2026, 8, 2));
        insertPromise(streamerId, currentSessionId, transcriptId, "third", "PENDING", null);
        insertPromise(streamerId, currentSessionId, transcriptId, "fourth", "PENDING", null);
        insertPromise(streamerId, currentSessionId, transcriptId, "done", "COMPLETED", null);

        jdbcTemplate.update(
                """
                INSERT INTO highlights (report_id, start_ms, end_ms, clip_url, thumbnail_url)
                VALUES (?, 10000, 55000, NULL, NULL)
                """,
                reportId
        );

        var upcoming = repository.findUpcomingBroadcast(
                streamerId, Instant.parse("2026-07-30T00:00:00Z")
        ).orElseThrow();
        assertThat(upcoming.title()).isEqualTo("next broadcast");

        var recent = repository.findRecentSession(streamerId).orElseThrow();
        assertThat(recent.sessionId()).isEqualTo(currentSessionId);
        assertThat(recent.avgViewers()).isEqualTo(200);
        assertThat(recent.summary()).isEqualTo("current summary");

        assertThat(repository.countPendingPromises(streamerId)).isEqualTo(4);
        assertThat(repository.findPendingPromises(streamerId, 3))
                .hasSize(3)
                .extracting(promise -> promise.content())
                .containsExactly("first", "second", "third");

        var weekly = repository.findWeeklyMetrics(
                streamerId, previousStart, currentStart, currentEnd
        );
        assertThat(weekly.hasCurrentData()).isTrue();
        assertThat(weekly.currentAvgViewers()).isEqualTo(200);
        assertThat(weekly.currentTotalChat()).isEqualTo(1500);
        assertThat(weekly.previousAvgViewers()).isEqualTo(100);
        assertThat(weekly.previousTotalChat()).isEqualTo(1000);

        assertThat(repository.findRecentHighlights(streamerId, 5))
                .singleElement()
                .satisfies(highlight -> {
                    assertThat(highlight.durationMs()).isEqualTo(45000);
                    assertThat(highlight.clipUrl()).isEmpty();
                    assertThat(highlight.thumbnailUrl()).isEmpty();
                });
    }

    private long createStreamer() {
        StreamerInsert streamer = new StreamerInsert(
                "dashboard-" + UUID.randomUUID() + "@example.com", "dashboard tester", null
        );
        authMapper.insertStreamer(streamer);
        return streamer.getId();
    }

    private long createPlatform(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "dashboard-" + UUID.randomUUID(), "dashboard channel",
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();
    }

    private long createEndedSession(
            long streamerId,
            long platformId,
            String title,
            Instant startedAt,
            long durationMinutes
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, category, language,
                     status, started_at, ended_at)
                VALUES (?, ?, ?, 'GAME', 'ko', 'ENDED', ?, ?)
                RETURNING id
                """,
                Long.class, streamerId, platformId, title, Timestamp.from(startedAt),
                Timestamp.from(startedAt.plus(durationMinutes, ChronoUnit.MINUTES))
        );
    }

    private long createReport(
            long sessionId,
            int avgViewers,
            int peakViewers,
            long totalChat,
            String summary
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO reports
                    (session_id, status, summary, avg_viewers, peak_viewers, total_chat, generated_at)
                VALUES (?, 'COMPLETED', ?, ?, ?, ?, now())
                RETURNING id
                """,
                Long.class, sessionId, summary, avgViewers, peakViewers, totalChat
        );
    }

    private void insertPromise(
            long streamerId,
            long sessionId,
            long transcriptId,
            String content,
            String status,
            LocalDate dueDate
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO promises
                    (streamer_id, session_id, transcript_id, content, status, due_date)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                streamerId, sessionId, transcriptId, content, status,
                dueDate == null ? null : Date.valueOf(dueDate)
        );
    }
}

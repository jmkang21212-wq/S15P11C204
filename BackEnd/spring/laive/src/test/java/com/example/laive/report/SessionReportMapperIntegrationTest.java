package com.example.laive.report;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.TriggerAnalysisStatus;
import com.example.laive.report.repository.SessionReportGenerationRepository;
import com.example.laive.report.repository.SessionReportQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class SessionReportMapperIntegrationTest {
    @Autowired
    private SessionReportQueryRepository queryRepository;
    @Autowired
    private SessionReportGenerationRepository generationRepository;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private PlatformAccountMapper platformAccountMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mapsCompleteReportGraphAndGenerationContext() {
        long streamerId = createStreamer();
        long platformId = createPlatform(streamerId);
        Instant startedAt = Instant.parse("2026-07-20T13:00:00Z");
        Instant endedAt = startedAt.plus(2, ChronoUnit.HOURS);
        long sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, category, language,
                     status, started_at, ended_at)
                VALUES (?, ?, '통합 테스트 방송', 'GAME', 'ko', 'ENDED', ?, ?)
                RETURNING id
                """,
                Long.class, streamerId, platformId, Timestamp.from(startedAt), Timestamp.from(endedAt)
        );
        long reportId = jdbcTemplate.queryForObject(
                """
                INSERT INTO reports
                    (session_id, status, summary, avg_viewers, peak_viewers, total_chat,
                     analyzed_chat, joy_count, excitement_count, support_count,
                     surprise_count, sadness_count, anger_count, confusion_count,
                     neutral_count, generated_at)
                VALUES (?, 'COMPLETED', '요약', 100, 200, 10,
                        10, 4, 2, 1, 1, 0, 0, 1, 1, now())
                RETURNING id
                """,
                Long.class, sessionId
        );
        jdbcTemplate.update(
                "INSERT INTO viewer_metrics (session_id, measured_at, viewer_count) VALUES (?, ?, 100)",
                sessionId, Timestamp.from(startedAt.plusSeconds(60))
        );
        long triggerId = jdbcTemplate.queryForObject(
                """
                INSERT INTO triggers
                    (session_id, trigger_type, start_ms, end_ms, started_at, ended_at,
                     peak_z, peak_engagement, avg_viewers, chat_count, unique_chatters,
                     analyzed_chat, joy_count, excitement_count, support_count,
                     surprise_count, sadness_count, anger_count, confusion_count,
                     neutral_count, narrative, analysis_status)
                VALUES (?, 'CHAT_SURGE', 10000, 50000, ?, ?,
                        5.2, 0.4, 120, 10, 8,
                        10, 4, 2, 1, 1, 0, 0, 1, 1, '급증 설명', 'COMPLETED')
                RETURNING id
                """,
                Long.class, sessionId, Timestamp.from(startedAt.plusSeconds(10)),
                Timestamp.from(startedAt.plusSeconds(50))
        );
        jdbcTemplate.update(
                "INSERT INTO report_insights (report_id, title, content) VALUES (?, '개선', '내용')",
                reportId
        );
        jdbcTemplate.update(
                "INSERT INTO highlights (report_id, trigger_id, start_ms, end_ms) VALUES (?, ?, 10000, 50000)",
                reportId, triggerId
        );

        var report = queryRepository.findReport(streamerId, sessionId).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(report.sentimentCounts().analyzedChat()).isEqualTo(10);
        assertThat(queryRepository.findViewerTimeline(sessionId)).hasSize(1);
        assertThat(queryRepository.findTriggers(sessionId))
                .singleElement()
                .extracting(trigger -> trigger.analysisStatus())
                .isEqualTo(TriggerAnalysisStatus.COMPLETED);
        assertThat(queryRepository.findInsights(reportId)).hasSize(1);
        assertThat(queryRepository.findHighlights(reportId))
                .singleElement()
                .extracting(highlight -> highlight.triggerId())
                .isEqualTo(triggerId);

        var context = generationRepository.findGenerationContext(reportId, sessionId).orElseThrow();
        assertThat(context.category()).isEqualTo("GAME");
        assertThat(context.durationMinutes()).isEqualTo(120);
        assertThat(context.hasCollectionGap()).isFalse();
    }

    private long createStreamer() {
        StreamerInsert streamer = new StreamerInsert(
                "report-" + UUID.randomUUID() + "@example.com", "리포트 테스트", null
        );
        authMapper.insertStreamer(streamer);
        return streamer.getId();
    }

    private long createPlatform(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "report-" + UUID.randomUUID(), "테스트 채널",
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();
    }
}

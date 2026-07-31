package com.example.laive.growth.roadmap;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.roadmap.ai.SuggestedGrowthGoal;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationStatus;
import com.example.laive.growth.roadmap.domain.RoadmapGenerationTrigger;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.domain.SuggestionStatus;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import com.example.laive.growth.roadmap.service.GrowthRoadmapInputService;
import com.example.laive.growth.roadmap.service.GrowthSuggestionCommandService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.growth.ai.enabled=false",
        "laive.growth.roadmap-ai.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class GrowthRoadmapIntegrationTest {
    @Autowired
    private GrowthRoadmapRepository repository;
    @Autowired
    private GrowthSuggestionCommandService suggestionCommands;
    @Autowired
    private GrowthRoadmapInputService inputService;
    @Autowired
    private AuthMapper authMapper;
    @Autowired
    private PlatformAccountMapper platformAccountMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesThreeSuggestionsAndAcceptsOneIdempotently() {
        long streamerId = createStreamer();
        long roadmapId = repository.insertRoadmap(streamerId, "growth-roadmap-v1");
        Instant now = Instant.parse("2026-08-01T03:00:00Z");
        long generationId = repository.insertGeneration(
                roadmapId, null, RoadmapGenerationTrigger.MANUAL,
                RoadmapGenerationStatus.PENDING, RoadmapQuality.ESTABLISHED,
                "growth-roadmap-v1", "roadmap-test:" + UUID.randomUUID(), now
        );
        repository.markGenerationProcessing(generationId, now);

        long firstSuggestionId = 0;
        for (int i = 0; i < 3; i++) {
            GrowthMetricType type = List.of(
                    GrowthMetricType.AVG_VIEWERS,
                    GrowthMetricType.TOTAL_CHAT,
                    GrowthMetricType.BROADCAST_MINUTES
            ).get(i);
            long suggestionId = repository.insertSuggestion(
                    roadmapId, generationId, i + 1, suggestion(type, i + 1)
            );
            if (i == 0) firstSuggestionId = suggestionId;
            repository.insertSuggestionActions(
                    suggestionId,
                    List.of(new GeneratedGrowthAction(
                            "실행 액션 " + i, "다음 방송에서 실행하고 결과를 측정합니다."
                    ))
            );
        }
        repository.markGenerationCompleted(generationId, "fingerprint", now);
        repository.markRoadmapReady(
                roadmapId, generationId, null, RoadmapQuality.ESTABLISHED,
                "growth-roadmap-v1", now
        );

        assertThat(repository.findCurrentSuggestions(roadmapId))
                .hasSize(3)
                .extracting(value -> value.metricType())
                .doesNotHaveDuplicates();
        assertThat(repository.findSuggestionActions(
                repository.findCurrentSuggestions(roadmapId).stream()
                        .map(value -> value.id()).toList()
        )).hasSize(3);

        var accepted = suggestionCommands.accept(streamerId, firstSuggestionId);
        var retried = suggestionCommands.accept(streamerId, firstSuggestionId);

        assertThat(accepted.created()).isTrue();
        assertThat(retried.created()).isFalse();
        assertThat(retried.goalId()).isEqualTo(accepted.goalId());
        assertThat(repository.findOwnedSuggestionForUpdate(
                streamerId, firstSuggestionId
        )).get().extracting(value -> value.status())
                .isEqualTo(SuggestionStatus.ACCEPTED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM growth_actions WHERE growth_goal_id = ? " +
                        "AND source_suggestion_id = ?",
                Integer.class, accepted.goalId(), firstSuggestionId
        )).isEqualTo(1);
    }

    @Test
    void assemblesAiInputWithoutPortfolioFromReportsAndBroadcastStatistics() {
        long streamerId = createStreamer();
        long platformId = createPlatform(streamerId);
        Instant endedAt = Instant.now();
        Instant startedAt = endedAt.minus(2, ChronoUnit.HOURS);
        long sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, category, language,
                     status, started_at, ended_at)
                VALUES (?, ?, '로드맵 입력 방송', 'GAME', 'ko', 'ENDED', ?, ?)
                RETURNING id
                """,
                Long.class, streamerId, platformId,
                java.sql.Timestamp.from(startedAt),
                java.sql.Timestamp.from(endedAt)
        );
        jdbcTemplate.update(
                """
                INSERT INTO reports (
                    session_id, status, summary, avg_viewers, peak_viewers,
                    total_chat, analyzed_chat, joy_count, excitement_count,
                    support_count, surprise_count, sadness_count, anger_count,
                    confusion_count, neutral_count, generated_at
                ) VALUES (
                    ?, 'COMPLETED', '방송 요약', 100, 180,
                    1000, 1000, 200, 200, 100, 100, 50, 50, 100, 200, now()
                )
                """,
                sessionId
        );

        var input = inputService.build(streamerId, "growth-roadmap-v1");

        assertThat(input.portfolio()).isEmpty();
        assertThat(input.recentReports()).singleElement()
                .satisfies(report -> {
                    assertThat(report.avgViewers()).isEqualTo(100);
                    assertThat(report.totalChat()).isEqualTo(1000);
                    assertThat(report.summary()).isEqualTo("방송 요약");
                });
        assertThat(input.currentValues().get(GrowthMetricType.AVG_VIEWERS))
                .isEqualByComparingTo("100.00");
        assertThat(input.inputFingerprint()).hasSize(64);
    }

    private SuggestedGrowthGoal suggestion(GrowthMetricType type, int index) {
        LocalDate start = LocalDate.of(2026, 8, 1);
        return new SuggestedGrowthGoal(
                type,
                new BigDecimal(100 * index),
                type.unit(),
                start,
                start.plusDays(55),
                type.name() + " 성장",
                "최근 방송 데이터에 근거한 성장 목표입니다.",
                List.of(new GeneratedGrowthAction(
                        "추천 액션", "다음 방송에서 실행하고 결과를 측정합니다."
                ))
        );
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                "roadmap-" + UUID.randomUUID() + "@example.com",
                "roadmap tester", null
        );
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatform(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "roadmap-" + UUID.randomUUID(),
                "roadmap channel", "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }
}

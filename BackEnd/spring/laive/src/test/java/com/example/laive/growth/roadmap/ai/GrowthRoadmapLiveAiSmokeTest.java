package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "laive.growth.roadmap-ai.enabled=true",
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE="
})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TEST", matches = "true")
class GrowthRoadmapLiveAiSmokeTest {
    @Autowired
    private GrowthRoadmapGenerator generator;

    @Test
    void generatesThreeGuardedSuggestionsThroughConfiguredProvider() {
        EnumMap<GrowthMetricType, BigDecimal> currentValues =
                new EnumMap<>(GrowthMetricType.class);
        currentValues.put(GrowthMetricType.AVG_VIEWERS, BigDecimal.valueOf(100));
        currentValues.put(GrowthMetricType.TOTAL_CHAT, BigDecimal.valueOf(1_000));
        currentValues.put(GrowthMetricType.BROADCAST_MINUTES, BigDecimal.valueOf(600));
        currentValues.put(GrowthMetricType.BROADCASTS_PER_WEEK, BigDecimal.valueOf(2));

        var input = new GrowthRoadmapInput(
                1L,
                Optional.empty(),
                List.of(new RoadmapTrendPoint(
                        java.time.LocalDate.now().minusDays(7),
                        java.time.LocalDate.now().minusDays(1),
                        100, 1_000, 600, BigDecimal.valueOf(2)
                )),
                List.of(new RoadmapSessionReport(
                        1L, 1L, "테스트 방송", "게임",
                        Instant.now(), "COMPLETED", 100, 150,
                        1_000, 900, 180,
                        "시청자 반응이 안정적으로 유지되었습니다.",
                        "오프닝 구간의 참여도가 높았습니다.",
                        "CHAT_SURGE: 오프닝 직후 채팅이 증가했습니다.",
                        "0-60000", "joy=500, neutral=400",
                        "", false
                )),
                new RecentBroadcastStatistics(
                        8, BigDecimal.valueOf(2), 1_440,
                        BigDecimal.valueOf(180), BigDecimal.valueOf(100),
                        150, 8_000, BigDecimal.valueOf(1_000), "게임"
                ),
                List.of(),
                List.of(),
                Map.copyOf(currentValues),
                RoadmapQuality.ESTABLISHED,
                "growth-roadmap-v1",
                "live-smoke-test"
        );

        var result = generator.generate(input);

        assertThat(result.goals()).hasSize(3);
        result.validateAgainst(input, java.time.LocalDate.now());
    }
}

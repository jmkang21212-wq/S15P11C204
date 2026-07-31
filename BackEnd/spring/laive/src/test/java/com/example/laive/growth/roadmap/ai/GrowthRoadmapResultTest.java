package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrowthRoadmapResultTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    @Test
    void acceptsThreeDistinctMeasurableSuggestions() {
        var result = new GrowthRoadmapResult(List.of(
                suggestion(GrowthMetricType.AVG_VIEWERS, "120"),
                suggestion(GrowthMetricType.TOTAL_CHAT, "1200"),
                suggestion(GrowthMetricType.BROADCAST_MINUTES, "1200")
        ));

        assertThatCode(() -> result.validateAgainst(input(), TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateMetrics() {
        assertThatThrownBy(() -> new GrowthRoadmapResult(List.of(
                suggestion(GrowthMetricType.AVG_VIEWERS, "120"),
                suggestion(GrowthMetricType.AVG_VIEWERS, "130"),
                suggestion(GrowthMetricType.TOTAL_CHAT, "1200")
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTargetAboveTwiceCurrentValue() {
        var result = new GrowthRoadmapResult(List.of(
                suggestion(GrowthMetricType.AVG_VIEWERS, "201"),
                suggestion(GrowthMetricType.TOTAL_CHAT, "1200"),
                suggestion(GrowthMetricType.BROADCAST_MINUTES, "1200")
        ));

        assertThatThrownBy(() -> result.validateAgainst(input(), TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SuggestedGrowthGoal suggestion(GrowthMetricType type, String target) {
        return new SuggestedGrowthGoal(
                type,
                new BigDecimal(target),
                type.unit(),
                TODAY,
                TODAY.plusDays(55),
                type.name() + " 성장",
                "최근 방송 데이터에 근거한 추천입니다.",
                List.of(new GeneratedGrowthAction(
                        "다음 방송 실험", "다음 방송에서 실행하고 해당 지표를 확인합니다."
                ))
        );
    }

    private GrowthRoadmapInput input() {
        EnumMap<GrowthMetricType, BigDecimal> values =
                new EnumMap<>(GrowthMetricType.class);
        values.put(GrowthMetricType.AVG_VIEWERS, new BigDecimal("100"));
        values.put(GrowthMetricType.TOTAL_CHAT, new BigDecimal("1000"));
        values.put(GrowthMetricType.BROADCAST_MINUTES, new BigDecimal("1000"));
        values.put(GrowthMetricType.BROADCASTS_PER_WEEK, new BigDecimal("2"));
        return new GrowthRoadmapInput(
                1L, Optional.empty(), List.of(), List.of(),
                new RecentBroadcastStatistics(
                        4, new BigDecimal("1.0"), 1000,
                        new BigDecimal("250"), new BigDecimal("100"),
                        150, 1000, new BigDecimal("250"), "게임"
                ),
                List.of(), List.of(), values, RoadmapQuality.ESTABLISHED,
                "growth-roadmap-v1", "fingerprint"
        );
    }
}

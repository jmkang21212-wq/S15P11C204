package com.example.laive.growth.roadmap.ai;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.domain.GrowthMetricType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuggestedGrowthGoalTest {
    private static final LocalDate START = LocalDate.of(2026, 8, 3);

    @Test
    void keepsPeriodEndInsideTheWindow() {
        var goal = goal(START.plusDays(55));

        assertThat(goal.periodEnd()).isEqualTo(START.plusDays(55));
    }

    @Test
    void clampsPeriodEndPastTheCeiling() {
        // 모델이 "84일"을 끝-시작 차이로 계산하면 포함 일수는 85가 된다. 잘라서 살린다.
        var goal = goal(START.plusDays(84));

        assertThat(goal.periodEnd()).isEqualTo(START.plusDays(83));
    }

    @Test
    void clampsPeriodEndBeforeTheFloor() {
        var goal = goal(START.plusDays(5));

        assertThat(goal.periodEnd()).isEqualTo(START.plusDays(27));
    }

    @Test
    void rejectsPeriodEndBeforePeriodStart() {
        assertThatThrownBy(() -> goal(START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precedes periodStart");
    }

    @Test
    void rejectsBlankRationale() {
        assertThatThrownBy(() -> new SuggestedGrowthGoal(
                GrowthMetricType.AVG_VIEWERS, new BigDecimal("120"),
                GrowthMetricType.AVG_VIEWERS.unit(), START, START.plusDays(55),
                "평균 시청자 성장", "  ", List.of(action())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rationale");
    }

    private SuggestedGrowthGoal goal(LocalDate periodEnd) {
        return new SuggestedGrowthGoal(
                GrowthMetricType.AVG_VIEWERS,
                new BigDecimal("120"),
                GrowthMetricType.AVG_VIEWERS.unit(),
                START,
                periodEnd,
                "평균 시청자 성장",
                "최근 방송 데이터에 근거한 추천입니다.",
                List.of(action())
        );
    }

    private GeneratedGrowthAction action() {
        return new GeneratedGrowthAction(
                "다음 방송 실험", "다음 방송에서 실행하고 해당 지표를 확인합니다."
        );
    }
}

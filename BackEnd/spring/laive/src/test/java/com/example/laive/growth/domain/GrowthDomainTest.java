package com.example.laive.growth.domain;

import com.example.laive.growth.exception.GrowthException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrowthDomainTest {
    @Test
    void validatesAlignedWeeklyAndMonthlyPeriods() {
        assertThat(GrowthPeriod.aligned(
                GrowthPeriodType.WEEKLY,
                LocalDate.parse("2026-07-13"),
                LocalDate.parse("2026-07-19")
        ).endExclusive()).hasToString("2026-07-20T00:00:00Z");

        assertThat(GrowthPeriod.aligned(
                GrowthPeriodType.MONTHLY,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31")
        ).startInclusive()).hasToString("2026-07-01T00:00:00Z");
    }

    @Test
    void rejectsUnalignedAndExcessivePeriods() {
        assertThatThrownBy(() -> GrowthPeriod.aligned(
                GrowthPeriodType.WEEKLY,
                LocalDate.parse("2026-07-14"),
                LocalDate.parse("2026-07-20")
        )).isInstanceOf(GrowthException.class)
                .extracting("code").isEqualTo("INVALID_GROWTH_PERIOD");
        assertThatThrownBy(() -> GrowthPeriod.aligned(
                GrowthPeriodType.MONTHLY,
                LocalDate.parse("2026-07-02"),
                LocalDate.parse("2026-07-31")
        )).isInstanceOf(GrowthException.class)
                .extracting("code").isEqualTo("INVALID_GROWTH_PERIOD");
        assertThatThrownBy(() -> GrowthPeriod.aligned(
                GrowthPeriodType.WEEKLY,
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2026-07-20")
        )).isInstanceOf(GrowthException.class)
                .extracting("code").isEqualTo("INVALID_GROWTH_PERIOD");
    }

    @Test
    void calculatesNullableChangeAndUncappedProgress() {
        assertThat(GrowthMath.changeRate(
                new BigDecimal("120"), new BigDecimal("100")
        )).isEqualByComparingTo("0.2000");
        assertThat(GrowthMath.changeRate(
                BigDecimal.TEN, BigDecimal.ZERO
        )).isEqualByComparingTo("0.0000");
        assertThat(GrowthMath.progress(
                new BigDecimal("120"), new BigDecimal("100")
        )).isEqualByComparingTo("1.2000");
    }

    @Test
    void validatesUnitAndStatusTransition() {
        assertThatThrownBy(() -> GrowthGoal.create(
                1L,
                GrowthMetricType.TOTAL_CHAT,
                BigDecimal.TEN,
                GrowthMetricUnit.COUNT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31")
        )).isInstanceOf(GrowthException.class);

        GrowthGoal cancelled = new GrowthGoal(
                1L, 1L, GrowthMetricType.TOTAL_CHAT, BigDecimal.TEN,
                GrowthMetricUnit.MESSAGES,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                GrowthGoalStatus.CANCELLED,
                1
        );
        assertThatThrownBy(() -> cancelled.update(
                null, BigDecimal.ONE, null, null, null, null
        )).isInstanceOf(GrowthException.class)
                .extracting("code").isEqualTo("GROWTH_GOAL_STATE_CONFLICT");
    }
}

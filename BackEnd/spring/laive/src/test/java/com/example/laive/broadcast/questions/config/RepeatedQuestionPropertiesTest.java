package com.example.laive.broadcast.questions.config;

import com.example.laive.broadcast.questions.config.RepeatedQuestionProperties.Reexpose;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 재노출 임계 계산의 경계값 — clamp(⌈시청자 × ratio⌉, min, max), 미상이면 fallback. */
class RepeatedQuestionPropertiesTest {

    private final Reexpose reexpose = new Reexpose(0.005, 5, 50, 10);

    @Test
    void scalesProportionallyBetweenClamps() {
        assertThat(reexpose.thresholdFor(2_000)).isEqualTo(10);  // 기존 고정 임계와의 연속성 기준점
        assertThat(reexpose.thresholdFor(5_000)).isEqualTo(25);
        assertThat(reexpose.thresholdFor(1_001)).isEqualTo(6);   // 올림: 5.005 → 6
    }

    @Test
    void clampsToMinForSmallChannels() {
        assertThat(reexpose.thresholdFor(1)).isEqualTo(5);
        assertThat(reexpose.thresholdFor(200)).isEqualTo(5);
        assertThat(reexpose.thresholdFor(1_000)).isEqualTo(5);   // 정확히 하한에 닿는 지점
    }

    @Test
    void clampsToMaxForLargeChannels() {
        assertThat(reexpose.thresholdFor(10_000)).isEqualTo(50); // 정확히 상한에 닿는 지점
        assertThat(reexpose.thresholdFor(300_000)).isEqualTo(50);
    }

    @Test
    void fallsBackWhenViewersUnknown() {
        assertThat(reexpose.thresholdFor(null)).isEqualTo(10);
        assertThat(reexpose.thresholdFor(0)).isEqualTo(10);
        assertThat(reexpose.thresholdFor(-1)).isEqualTo(10);
    }
}

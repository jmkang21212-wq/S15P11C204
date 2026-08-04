package com.example.laive.report.domain;

import com.example.laive.broadcast.realtime.domain.SentimentLabel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SentimentDistributionTest {
    @Test
    void calculatesAllEightRatiosAndDominant() {
        var result = SentimentDistribution.from(new SentimentCounts(
                10, 4, 2, 1, 1, 1, 0, 0, 1
        ));

        assertThat(result.dominant()).isEqualTo(SentimentLabel.JOY);
        assertThat(result.distribution().get(SentimentLabel.JOY))
                .isEqualByComparingTo(new BigDecimal("0.4000"));
        assertThat(result.distribution()).hasSize(8);
    }

    @Test
    void emptyAnalysisUsesNeutralAndZeroRatios() {
        var result = SentimentDistribution.from(new SentimentCounts(
                0, 0, 0, 0, 0, 0, 0, 0, 0
        ));

        assertThat(result.dominant()).isEqualTo(SentimentLabel.NEUTRAL);
        assertThat(result.distribution().values())
                .allMatch(value -> value.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void rejectsInconsistentCounts() {
        assertThatThrownBy(() -> new SentimentCounts(
                2, 1, 0, 0, 0, 0, 0, 0, 0
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

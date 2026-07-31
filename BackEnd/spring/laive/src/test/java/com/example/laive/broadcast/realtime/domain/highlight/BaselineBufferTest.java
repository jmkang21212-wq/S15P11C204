package com.example.laive.broadcast.realtime.domain.highlight;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * median·MAD 기반 z-score와 MAD 하한(가드 2)을 검증한다.
 */
class BaselineBufferTest {

    private static final double MAD_SCALE = 1.4826;

    @Test
    void madFloorAppliesWhenMadCollapses() {
        // 같은 값으로 도배된 baseline — MAD가 0으로 붕괴하는 소규모 채널 상황.
        BaselineBuffer buffer = new BaselineBuffer(10);
        for (int i = 0; i < 10; i++) {
            buffer.add(0.15);
        }

        // 하한이 분모를 대신한다: z = (e − med) / (1.4826 × floor).
        double z = buffer.zScore(0.20, 0.025);
        assertThat(z).isCloseTo(0.05 / (MAD_SCALE * 0.025), within(1e-9));
    }

    @Test
    void realMadUsedWhenDispersionExceedsFloor() {
        BaselineBuffer buffer = new BaselineBuffer(5);
        for (double value : new double[]{0.1, 0.2, 0.3, 0.4, 0.5}) {
            buffer.add(value);
        }

        // median 0.3, MAD 0.1 > 하한 0.001 → 실제 MAD를 쓴다.
        double z = buffer.zScore(0.6, 0.001);
        assertThat(z).isCloseTo(0.3 / (MAD_SCALE * 0.1), within(1e-9));
    }

    @Test
    void interpolatesMedianForEvenSize() {
        BaselineBuffer buffer = new BaselineBuffer(10);
        buffer.add(0.1);
        buffer.add(0.2);

        // median 0.15, 편차 [0.05, 0.05] → MAD 0.05.
        double z = buffer.zScore(0.25, 0.001);
        assertThat(z).isCloseTo(0.1 / (MAD_SCALE * 0.05), within(1e-9));
    }

    @Test
    void ringBufferEvictsOldestBeyondCapacity() {
        BaselineBuffer buffer = new BaselineBuffer(3);
        buffer.add(0.1); // 용량 초과로 밀려난다
        buffer.add(0.2);
        buffer.add(0.3);
        buffer.add(0.9);

        assertThat(buffer.size()).isEqualTo(3);
        // 남은 값 [0.2, 0.3, 0.9]: median 0.3 → engagement 0.3의 z는 0이다.
        assertThat(buffer.zScore(0.3, 0.001)).isCloseTo(0.0, within(1e-9));
    }
}

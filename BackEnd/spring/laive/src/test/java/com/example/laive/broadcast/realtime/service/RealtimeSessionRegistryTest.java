package com.example.laive.broadcast.realtime.service;

import com.example.laive.broadcast.realtime.config.HighlightProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 시청자 수 신선도 규칙을 검증한다 — 기준은 서버 수신 시각이지 measuredAt
 * (수집기 시계)이 아니다(HIGHLIGHT_SPEC 가드 8).
 */
class RealtimeSessionRegistryTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    /** 테스트가 시각을 직접 미는 시계. Clock.fixed는 전진이 안 된다. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void stalenessUsesServerReceiveTimeNotCollectorClock() {
        // 수집기 시계가 서버보다 9시간 뒤처진 상황(Docker VM 드리프트 등).
        // measuredAt 기준이면 도착 즉시 "5분 초과"로 버려져 판정기가 no-viewers에 갇힌다.
        MutableClock clock = new MutableClock(NOW);
        RealtimeSessionRegistry registry =
                new RealtimeSessionRegistry(mock(HighlightProperties.class), clock);

        registry.recordViewers(1L, 100, NOW.minus(Duration.ofHours(9)));

        assertThat(registry.currentViewers(1L)).isEqualTo(100);
    }

    @Test
    void viewersExpireWhenNothingReceivedForFiveMinutes() {
        MutableClock clock = new MutableClock(NOW);
        RealtimeSessionRegistry registry =
                new RealtimeSessionRegistry(mock(HighlightProperties.class), clock);
        registry.recordViewers(1L, 100, NOW);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        // 수신이 5분 넘게 끊기면 가드 4대로 모른다고 답한다.
        assertThat(registry.currentViewers(1L)).isNull();
    }

    @Test
    void lateRedeliveryOfOlderMeasurementIsIgnored() {
        // "어느 쪽이 최신 측정인가"는 같은 수집기 시계끼리의 비교라 measuredAt이 맞다.
        MutableClock clock = new MutableClock(NOW);
        RealtimeSessionRegistry registry =
                new RealtimeSessionRegistry(mock(HighlightProperties.class), clock);

        registry.recordViewers(1L, 120, NOW);
        registry.recordViewers(1L, 99, NOW.minusSeconds(30)); // 재전송된 과거 배치

        assertThat(registry.currentViewers(1L)).isEqualTo(120);
    }
}

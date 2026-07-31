package com.example.laive.media.service;

import com.example.laive.media.config.ClipProperties;
import com.example.laive.media.domain.ClipCut;
import com.example.laive.media.domain.RecordingTimebase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 오프셋 계산은 틀려도 클립이 정상적으로 만들어지고 업로드도 성공한다. 엉뚱한
 * 장면이 담겼다는 것은 사람이 열어봐야 알고 확인 한 번에 실방송 한 번이 들기 때문에,
 * 기대값을 여기에 숫자로 고정해 둔다.
 */
class ClipOffsetCalculatorTest {

    private static final Instant BASE_WALL_CLOCK = Instant.parse("2026-07-31T12:15:03.400Z");
    private static final long BASE_TIMECODE_MS = 4_503_400;

    private final ClipOffsetCalculator calculator = new ClipOffsetCalculator(
            new ClipProperties(Duration.ofMillis(4_500), 3, "/tmp/clips", "http://localhost/clips", 200)
    );

    @Test
    void 송출_지연을_뺀_녹화_타임코드를_돌려준다() {
        var timebase = new RecordingTimebase(BASE_WALL_CLOCK, BASE_TIMECODE_MS);

        var cut = calculator.calculate(Instant.parse("2026-07-31T12:15:00Z"), 80_000, timebase);

        // 기준점보다 3.4초 이르므로 4,500,000ms 지점이고, 송출 지연 4.5초를 뺀다.
        assertThat(cut).contains(new ClipCut(4_495_500, 80_000));
        assertThat(cut.orElseThrow().startSec()).isEqualTo(4495.5);
    }

    @Test
    void 기준점의_타임코드가_드리프트되어_있으면_그_값을_따른다() {
        // 같은 시각인데 녹화는 400ms 덜 흘렀다 — 프레임 드랍으로 벌어진 차이다.
        var drifted = new RecordingTimebase(BASE_WALL_CLOCK, BASE_TIMECODE_MS - 400);

        var cut = calculator.calculate(Instant.parse("2026-07-31T12:15:00Z"), 80_000, drifted);

        assertThat(cut).contains(new ClipCut(4_495_100, 80_000));
    }

    @Test
    void 구간_앞부분이_녹화_이전이면_끝점을_고정하고_시작만_당긴다() {
        // 녹화가 방송보다 2초 늦게 시작됐다.
        var late = new RecordingTimebase(Instant.parse("2026-07-31T11:00:02Z"), 0);

        var cut = calculator.calculate(Instant.parse("2026-07-31T11:00:00Z"), 80_000, late);

        // 원래 시작은 -6,500ms(2초 지각 + 4.5초 보정)다. 없는 앞부분을 버리고
        // 끝점 73,500ms는 그대로 둔다 — 반응이 일어난 시점이 구간의 의미다.
        assertThat(cut).contains(new ClipCut(0, 73_500));
    }

    @Test
    void 구간이_통째로_녹화_이전이면_클립을_만들지_않는다() {
        var late = new RecordingTimebase(Instant.parse("2026-07-31T11:00:02Z"), 0);

        var cut = calculator.calculate(Instant.parse("2026-07-31T11:00:00Z"), 5_000, late);

        assertThat(cut).isEmpty();
    }

    @Test
    void 길이가_0_이하인_구간은_거부한다() {
        var timebase = new RecordingTimebase(BASE_WALL_CLOCK, BASE_TIMECODE_MS);

        assertThatThrownBy(() -> calculator.calculate(BASE_WALL_CLOCK, 0, timebase))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 트리거_앵커가_있으면_그_시각을_그대로_쓴다() {
        var anchor = Instant.parse("2026-07-31T12:15:00Z");

        var resolved = calculator.resolveStart(
                4412, anchor, Instant.parse("2026-07-31T11:00:00Z"), 4_500_000
        );

        assertThat(resolved).isEqualTo(anchor);
    }

    @Test
    void 트리거_앵커가_없으면_세션_시작에_오프셋을_더한다() {
        var sessionStart = Instant.parse("2026-07-31T11:00:00Z");

        var resolved = calculator.resolveStart(4412, null, sessionStart, 4_500_000);

        assertThat(resolved).isEqualTo(Instant.parse("2026-07-31T12:15:00Z"));
    }

    @Test
    void 기준점의_타임코드는_음수일_수_없다() {
        assertThatThrownBy(() -> new RecordingTimebase(BASE_WALL_CLOCK, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

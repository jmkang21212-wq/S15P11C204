package com.example.laive.broadcast.realtime.domain.highlight;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IDLE → ACTIVE → 확정 → COOLDOWN 전이를 스펙 §3 규칙대로 검증한다.
 */
class HighlightStateMachineTest {

    private static final Instant T0 = Instant.parse("2026-07-21T12:00:00Z");

    private static final HighlightParams PARAMS = new HighlightParams(
            Duration.ofSeconds(60), 900,
            3.0, 3,
            1.5, 15,
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofMinutes(3),
            true, // baselineDuringWarmup — 상태기계는 읽지 않는다
            10,
            0.001,
            0.5   // madFloorChatters — 상태기계는 읽지 않는다
    );

    @Test
    void confirmsSegmentWithLeadBackdatedStartAndFirstExitTickEnd() {
        HighlightStateMachine machine = new HighlightStateMachine(PARAMS);

        // 진입: z ≥ 3.0이 3틱 연속이어야 ACTIVE가 된다.
        assertThat(machine.onTick(at(0), 5.0, 0.10)).isEmpty();
        assertThat(machine.onTick(at(1), 5.0, 0.12)).isEmpty();
        assertThat(machine.onTick(at(2), 5.0, 0.15)).isEmpty();
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.ACTIVE);

        // 구간 중 최댓값 추적.
        assertThat(machine.onTick(at(3), 7.5, 0.31)).isEmpty();

        // 이탈: z ≤ 1.5가 15틱 연속. 마지막 틱에서 확정된다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 0; i < 15; i++) {
            confirmed = machine.onTick(at(4 + i), 1.0, 0.05);
        }

        assertThat(confirmed).isPresent();
        HighlightCandidate candidate = confirmed.orElseThrow();
        // 시작은 진입 확정 틱(t2)에서 리드 30초 소급.
        assertThat(candidate.startedAt()).isEqualTo(at(2).minusSeconds(30));
        // 끝은 이탈이 시작된 첫 틱(t4). 확정 틱이 아니다.
        assertThat(candidate.endedAt()).isEqualTo(at(4));
        assertThat(candidate.peakZ()).isEqualTo(7.5);
        assertThat(candidate.peakEngagement()).isEqualTo(0.31);
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.COOLDOWN);
    }

    @Test
    void resetsEnterStreakWhenZDrops() {
        HighlightStateMachine machine = new HighlightStateMachine(PARAMS);

        machine.onTick(at(0), 5.0, 0.1);
        machine.onTick(at(1), 5.0, 0.1);
        machine.onTick(at(2), 1.0, 0.05); // 연속이 끊긴다
        machine.onTick(at(3), 5.0, 0.1);
        machine.onTick(at(4), 5.0, 0.1);

        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.IDLE);
    }

    @Test
    void resetsExitStreakWhenZBouncesBack() {
        HighlightStateMachine machine = enterActive();

        // 이탈 14틱 뒤 반등 → 카운트 리셋.
        for (int i = 0; i < 14; i++) {
            assertThat(machine.onTick(at(10 + i), 1.0, 0.05)).isEmpty();
        }
        assertThat(machine.onTick(at(24), 4.0, 0.2)).isEmpty();

        // 두 번째 이탈 연속의 첫 틱이 구간의 끝이 된다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 0; i < 15; i++) {
            confirmed = machine.onTick(at(25 + i), 1.0, 0.05);
        }
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(25));
    }

    @Test
    void forcesFinalizationAfterMaxActive() {
        HighlightStateMachine machine = enterActive();

        // 계속 뜨거우면 이탈 조건이 영영 안 오므로 지속 상한이 구간을 끝낸다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 10; confirmed.isEmpty() && i < 400; i++) {
            confirmed = machine.onTick(at(i), 6.0, 0.2);
        }

        assertThat(confirmed).isPresent();
        // 진입 확정(t2) + 5분 = t302에서 강제 확정.
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(302));
    }

    @Test
    void cooldownSuppressesImmediateReentry() {
        HighlightStateMachine machine = enterActive();
        for (int i = 0; i < 15; i++) {
            machine.onTick(at(10 + i), 1.0, 0.05);
        }
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.COOLDOWN);

        // 확정 직후 30초 안의 높은 z는 무시된다.
        machine.onTick(at(25), 9.0, 0.4);
        machine.onTick(at(26), 9.0, 0.4);
        machine.onTick(at(27), 9.0, 0.4);
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.COOLDOWN);

        // COOLDOWN이 끝나면(확정 틱 + 30초) 다시 판정한다.
        machine.onTick(at(55), 9.0, 0.4);
        machine.onTick(at(56), 9.0, 0.4);
        machine.onTick(at(57), 9.0, 0.4);
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.ACTIVE);
    }

    @Test
    void forceFinalizeReturnsActiveSegmentOnly() {
        HighlightStateMachine idle = new HighlightStateMachine(PARAMS);
        assertThat(idle.forceFinalize(at(100))).isEmpty();

        HighlightStateMachine active = enterActive();
        Optional<HighlightCandidate> confirmed = active.forceFinalize(at(100));
        assertThat(confirmed).isPresent();
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(100));
    }

    @Test
    void skippedTickKeepsStreaksButEnforcesMaxActive() {
        HighlightStateMachine machine = enterActive();

        // 판정 불가 틱은 이탈 카운트를 건드리지 않는다(연장도 리셋도 안 함).
        for (int i = 0; i < 14; i++) {
            machine.onTick(at(10 + i), 1.0, 0.05);
        }
        assertThat(machine.onSkippedTick(at(24))).isEmpty();
        // 카운트가 리셋되지 않았다면 다음 이탈 틱에서 확정된다.
        assertThat(machine.onTick(at(25), 1.0, 0.05)).isPresent();
    }

    @Test
    void quietTicksCountTowardExitAndEndAtFirstQuietTick() {
        HighlightStateMachine machine = enterActive();

        // 저활동 틱은 이탈 표다. exitTicks(15)를 채우면 확정된다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 0; i < 15; i++) {
            confirmed = machine.onQuietTick(at(10 + i));
        }

        assertThat(confirmed).isPresent();
        // 끝은 이탈 스트릭의 첫 틱 — 저활동 틱이어도 같다.
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(10));
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.COOLDOWN);
    }

    @Test
    void mixedQuietAndJudgedExitTicksShareOneStreak() {
        HighlightStateMachine machine = enterActive();

        // 정상 이탈 7표 + 저활동 1표 + 정상 이탈 7표 = 15표에서 확정.
        for (int i = 0; i < 7; i++) {
            assertThat(machine.onTick(at(10 + i), 1.0, 0.05)).isEmpty();
        }
        assertThat(machine.onQuietTick(at(17))).isEmpty();
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 0; i < 7; i++) {
            confirmed = machine.onTick(at(18 + i), 1.0, 0.05);
        }

        assertThat(confirmed).isPresent();
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(10));
    }

    @Test
    void judgedBounceResetsQuietExitStreak() {
        HighlightStateMachine machine = enterActive();

        // 저활동 5표 뒤 정상 반등 틱만이 스트릭을 리셋한다.
        for (int i = 0; i < 5; i++) {
            assertThat(machine.onQuietTick(at(10 + i))).isEmpty();
        }
        assertThat(machine.onTick(at(15), 4.0, 0.2)).isEmpty();

        // 새 스트릭이 저활동 틱으로 시작하면 그 틱이 구간의 끝이 된다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 0; i < 15; i++) {
            confirmed = machine.onQuietTick(at(16 + i));
        }
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(16));
    }

    @Test
    void quietTickEnforcesMaxActive() {
        HighlightStateMachine machine = enterActive();

        // 계속 뜨거워 이탈 표가 없는 채로 상한 직전까지 간다.
        for (int i = 10; i < 302; i++) {
            assertThat(machine.onTick(at(i), 6.0, 0.2)).isEmpty();
        }
        // 진입 확정(t2) + 5분 = t302. 저활동 틱도 상한을 지킨다.
        Optional<HighlightCandidate> confirmed = machine.onQuietTick(at(302));
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(302));
    }

    @Test
    void quietTickFreezesEnterStreakInIdleAndCooldown() {
        // IDLE: 저활동 틱은 진입 스트릭을 리셋하지 않는다(동결).
        HighlightStateMachine machine = new HighlightStateMachine(PARAMS);
        machine.onTick(at(0), 5.0, 0.1);
        machine.onTick(at(1), 5.0, 0.1);
        assertThat(machine.onQuietTick(at(2))).isEmpty();
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.IDLE);
        machine.onTick(at(3), 5.0, 0.1);
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.ACTIVE);

        // COOLDOWN: 저활동 틱은 아무것도 하지 않는다.
        HighlightStateMachine cooled = enterActive();
        for (int i = 0; i < 15; i++) {
            cooled.onTick(at(10 + i), 1.0, 0.05);
        }
        assertThat(cooled.phase()).isEqualTo(HighlightStateMachine.Phase.COOLDOWN);
        assertThat(cooled.onQuietTick(at(26))).isEmpty();
        assertThat(cooled.phase()).isEqualTo(HighlightStateMachine.Phase.COOLDOWN);
    }

    /** t0~t2에 z=5를 넣어 ACTIVE로 만든다. 진입 확정 틱은 t2다. */
    private HighlightStateMachine enterActive() {
        HighlightStateMachine machine = new HighlightStateMachine(PARAMS);
        machine.onTick(at(0), 5.0, 0.1);
        machine.onTick(at(1), 5.0, 0.1);
        machine.onTick(at(2), 5.0, 0.1);
        assertThat(machine.phase()).isEqualTo(HighlightStateMachine.Phase.ACTIVE);
        return machine;
    }

    private static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }
}

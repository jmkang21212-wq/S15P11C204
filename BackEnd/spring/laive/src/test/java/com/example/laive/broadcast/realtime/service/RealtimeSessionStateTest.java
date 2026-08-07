package com.example.laive.broadcast.realtime.service;

import com.example.laive.broadcast.realtime.domain.highlight.HighlightCandidate;
import com.example.laive.broadcast.realtime.domain.highlight.HighlightParams;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HIGHLIGHT_SPEC의 가드(워밍업·저활동·시청자 결측·수집 공백)를 시나리오로 검증한다.
 *
 * <p>판정 파라미터를 작게 줄인 이유: 실제 값(워밍업 3분, 윈도우 60초)으로는
 * 시나리오마다 수백 틱을 돌려야 해 테스트의 의도가 묻힌다. 전이 규칙 자체는
 * {@code HighlightStateMachineTest}가 실제 파라미터로 검증한다.
 */
class RealtimeSessionStateTest {

    private static final Instant START = Instant.parse("2026-07-21T12:00:00Z");
    private static final int VIEWERS = 100;

    private static HighlightParams params(int minWindowChats) {
        // 동적 하한 0.0 = max(ε, 0)이라 ε만 남아, 기존 가드 시나리오의 z 수치가 보존된다.
        return params(minWindowChats, 0.0);
    }

    private static HighlightParams params(int minWindowChats, double madFloorChatters) {
        return params(minWindowChats, madFloorChatters, 1.5);
    }

    private static HighlightParams params(int minWindowChats, double madFloorChatters, double zExit) {
        return params(minWindowChats, madFloorChatters, zExit, true);
    }

    private static HighlightParams params(
            int minWindowChats, double madFloorChatters, double zExit, boolean baselineDuringWarmup) {
        return new HighlightParams(
                Duration.ofSeconds(10), 60,
                3.0, 2,
                zExit, 3,
                Duration.ofSeconds(5),
                Duration.ofSeconds(120),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),   // 워밍업 10초
                baselineDuringWarmup,
                minWindowChats,
                0.001,
                madFloorChatters
        );
    }

    @Test
    void confirmsSurgeAfterWarmupBaseline() {
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));

        // 워밍업 동안 같은 3명이 매초 채팅 → baseline이 engagement 0.03으로 찬다.
        int t = warmUpWithSteadyChat(state);

        // 급증: 매초 새 uid 3명이 가세 → 유니크가 쌓여 z가 뛴다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        int surgeStart = t;
        while (confirmed.isEmpty() && t < surgeStart + 10) {
            feedSteady(state, t);
            feedNewcomers(state, t, 3);
            confirmed = state.tick(at(t), VIEWERS);
            t++;
        }
        // enterTicks=2 뒤 ACTIVE. 확정은 종료 강제로 확인한다.
        assertThat(state.forceFinalize(at(t))).isPresent();
    }

    @Test
    void warmupSuppressesJudgment() {
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));

        // 워밍업 안에서는 아무리 급증해도 ACTIVE가 되지 않는다(가드 1).
        for (int t = 0; t < 10; t++) {
            feedNewcomers(state, t, 10);
            assertThat(state.tick(at(t), VIEWERS)).isEmpty();
        }
        assertThat(state.forceFinalize(at(10))).isEmpty();
    }

    @Test
    void missingViewersSuppressesJudgment() {
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));
        int t = warmUpWithSteadyChat(state);

        // 시청자 수를 모르면 engagement를 계산할 수 없다(가드 4).
        for (int i = 0; i < 5; i++, t++) {
            feedSteady(state, t);
            feedNewcomers(state, t, 10);
            assertThat(state.tick(at(t), null)).isEmpty();
        }
        assertThat(state.forceFinalize(at(t))).isEmpty();
    }

    @Test
    void staleViewerCountIsLiftedToWindowChatters() {
        // 치지직 집계 지연: 채팅한 사람이 3명인데 시청자 집계는 1명에 머무는 틱.
        // 분모를 채터 수로 끌어올려 engagement가 1.0을 넘지 않아야 한다 —
        // 2.0·3.0짜리 지연 아티팩트가 baseline을 선점하면 집계가 따라잡은 뒤의
        // 진짜 버스트가 평소 이하로 읽힌다(가드 4 확장, 2026-08-07 실측).
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));
        int t = warmUpWithSteadyChat(state);

        feedSteady(state, t);
        state.tick(at(t), 1);

        assertThat(state.view().skipReason()).isNull();
        assertThat(state.view().engagement()).isEqualTo(1.0);
    }

    @Test
    void chatsWhileReportedZeroViewersAreStillJudged() {
        // 집계가 0인데 채팅이 들어오는 틱은 무신호가 아니라 집계 지연이다 — 판정한다.
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));
        int t = warmUpWithSteadyChat(state);

        feedSteady(state, t);
        state.tick(at(t), 0);
        assertThat(state.view().skipReason()).isNull();

        // 채팅까지 없는 0명 틱만 진짜 무신호라 zero-viewers로 쉰다.
        state.tick(at(t + 20), 0); // 윈도우(10초)가 완전히 비도록 건너뛴다
        assertThat(state.view().skipReason()).isEqualTo("zero-viewers");
    }

    @Test
    void lowActivityTickIsNotJudged() {
        // 윈도우 채팅 하한을 높게 잡아 급증 uid 수가 하한에 못 미치게 한다(가드 3).
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(200));
        int t = warmUpWithSteadyChat(state);

        for (int i = 0; i < 5; i++, t++) {
            feedSteady(state, t);
            feedNewcomers(state, t, 10);
            assertThat(state.tick(at(t), VIEWERS)).isEmpty();
        }
        assertThat(state.forceFinalize(at(t))).isEmpty();
    }

    @Test
    void gapSuppressesJudgmentUntilWindowClearsIt() {
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));
        int t = warmUpWithSteadyChat(state);

        // 공백 진행 중: 판정 정지(가드 9).
        state.onGap(null);
        for (int i = 0; i < 3; i++, t++) {
            feedSteady(state, t);
            feedNewcomers(state, t, 10);
            assertThat(state.tick(at(t), VIEWERS)).isEmpty();
        }
        assertThat(state.forceFinalize(at(t))).isEmpty();

        // 공백이 닫혀도 윈도우(10초)가 공백에 걸치는 동안은 계속 쉰다.
        RealtimeSessionState reopened = new RealtimeSessionState(1L, START, params(3));
        int u = warmUpWithSteadyChat(reopened);
        reopened.onGap(null);
        u += 2;
        reopened.onGap(at(u)); // 공백 종료
        for (int i = 0; i < 9; i++, u++) {
            feedSteady(reopened, u);
            feedNewcomers(reopened, u, 10);
            assertThat(reopened.tick(at(u), VIEWERS)).isEmpty();
        }
        // 공백 종료 + 10초가 지나면 판정이 재개된다.
        u += 2;
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (int i = 0; confirmed.isEmpty() && i < 10; i++, u++) {
            feedSteady(reopened, u);
            feedNewcomers(reopened, u, 10);
            confirmed = reopened.tick(at(u), VIEWERS);
            if (reopened.forceFinalize(at(u)).isPresent()) {
                return; // ACTIVE 진입 확인으로 충분
            }
        }
        assertThat(confirmed).isPresent();
    }

    @Test
    void dynamicMadFloorSuppressesSmallChannelNoise() {
        // 소규모 채널(시청자 20명): baseline이 같은 값으로 도배되어 MAD가 붕괴한다.
        // 동적 하한이 없으면(0.0 → ε=0.001) 채터 +1명에 z가 폭발해 진입한다.
        int viewers = 20;

        RealtimeSessionState floored = new RealtimeSessionState(1L, START, params(3, 0.5));
        RealtimeSessionState bare = new RealtimeSessionState(1L, START, params(3, 0.0));
        for (int t = 0; t < 11; t++) {
            feedSteady(floored, t);
            feedSteady(bare, t);
            floored.tick(at(t), viewers);
            bare.tick(at(t), viewers);
        }

        // 같은 +1명 bump: engagement 0.15 → 0.20.
        for (int t = 11; t < 16; t++) {
            floored.feedChats(at(t), List.of("u1", "u2", "u3", "extra"));
            bare.feedChats(at(t), List.of("u1", "u2", "u3", "extra"));
            // 하한 0.5/20 = 0.025 → z ≈ 1.35로 진입하지 않는다.
            assertThat(floored.tick(at(t), viewers)).isEmpty();
            bare.tick(at(t), viewers);
        }

        assertThat(floored.forceFinalize(at(16))).isEmpty();
        // 하한이 없으면 같은 bump가 z ≈ 34로 진입한다 — 대조 검증.
        assertThat(bare.forceFinalize(at(16))).isPresent();
    }

    @Test
    void excludingWarmupBaselineKeepsGreetingRushFromPresettingMedian() {
        // 시연 시나리오: 워밍업 내내 인사 러시 → 짧은 정적 → 소규모 버스트(매초 새 3명).
        // 러시를 baseline에 적재하면(기본) median이 러시 수준으로 선점되어 버스트가
        // "평소 이하"로 읽혀 묻힌다. 제외 모드는 baseline뿐 아니라 워밍업 안에 받은
        // 채팅을 판정 윈도우에서도 빼므로, 러시가 워밍업 끝까지 이어져도 워밍업 종료
        // 몇 초 뒤의 버스트가 바로 잡힌다 — 정적이 윈도우 길이(10초)만큼 필요하지 않다.
        RealtimeSessionState excluded =
                new RealtimeSessionState(1L, START, params(3, 0.0, 1.5, false));
        RealtimeSessionState included = new RealtimeSessionState(1L, START, params(3));

        // 인사 러시: 워밍업(10초) 내내 매초 새 10명 — 제외 모드가 없으면 이 채팅이
        // 윈도우(10초)에 남아 워밍업 종료(t=10) 후까지 물려 나온다.
        for (int t = 0; t < 10; t++) {
            feedNewcomers(excluded, t, 10);
            feedNewcomers(included, t, 10);
            excluded.tick(at(t), VIEWERS);
            included.tick(at(t), VIEWERS);
        }
        // 짧은 정적: baseline이 조용한 표본으로 시드될 시간만 준다(윈도우 소진 대기 불필요).
        for (int t = 10; t < 13; t++) {
            excluded.tick(at(t), VIEWERS);
            included.tick(at(t), VIEWERS);
        }
        // 버스트: 같은 입력을 양쪽에 공급한다. 제외 모드는 baseline이 작아 버스트
        // 자신의 램프가 금방 '평소'가 되고 z가 이탈 임계 밑으로 내려오므로, 구간이
        // 루프 안에서 이미 확정될 수 있다 — 진입만이 아니라 확정까지를 신호로 본다.
        boolean excludedConfirmed = false;
        boolean includedConfirmed = false;
        for (int t = 13; t < 25; t++) {
            feedSteady(excluded, t);
            feedNewcomers(excluded, t, 3);
            feedSteady(included, t);
            feedNewcomers(included, t, 3);
            excludedConfirmed |= excluded.tick(at(t), VIEWERS).isPresent();
            includedConfirmed |= included.tick(at(t), VIEWERS).isPresent();
        }

        assertThat(excludedConfirmed || excluded.forceFinalize(at(25)).isPresent()).isTrue();
        // 대조: 워밍업 표본이 baseline과 윈도우에 남아 있으면 같은 버스트가 진입하지 못한다.
        assertThat(includedConfirmed).isFalse();
        assertThat(included.forceFinalize(at(25))).isEmpty();
    }

    @Test
    void quietTicksWhileActiveConfirmSegment() {
        // zExit를 도달 불가능하게 두면 정상 판정 틱은 이탈 표를 못 낸다.
        // 그런데도 확정된다면 저활동 틱(가드 3)이 이탈 표로 산입됐다는 뜻이다.
        RealtimeSessionState state =
                new RealtimeSessionState(1L, START, params(3, 0.0, -100.0));
        int t = warmUpWithSteadyChat(state);

        // t=11, 12에 급증 → enterTicks=2로 ACTIVE. 마지막 채팅 공급은 t=12다.
        for (; t < 13; t++) {
            feedSteady(state, t);
            feedNewcomers(state, t, 3);
            state.tick(at(t), VIEWERS);
        }

        // 공급 중단 → 윈도우(10초)가 t=23에 비면서 저활동 틱이 시작된다.
        Optional<HighlightCandidate> confirmed = Optional.empty();
        for (; confirmed.isEmpty() && t < 30; t++) {
            confirmed = state.tick(at(t), VIEWERS);
        }

        // exitTicks=3: t=23, 24, 25에서 확정. 끝은 첫 저활동 틱이다.
        assertThat(confirmed.orElseThrow().endedAt()).isEqualTo(at(23));
    }

    @Test
    void missingViewersDuringActiveDoesNotCountExit() {
        RealtimeSessionState state = new RealtimeSessionState(1L, START, params(3));
        int t = warmUpWithSteadyChat(state);

        // ACTIVE 진입.
        for (; t < 13; t++) {
            feedSteady(state, t);
            feedNewcomers(state, t, 3);
            state.tick(at(t), VIEWERS);
        }

        // 시청자 결측(가드 4)은 저활동(가드 3)과 달리 이탈 표가 아니다 — 동결.
        for (int i = 0; i < 10; i++, t++) {
            assertThat(state.tick(at(t), null)).isEmpty();
        }
        assertThat(state.forceFinalize(at(t))).isPresent();
    }

    /** 워밍업(10초) 동안 같은 3명이 매초 채팅해 baseline을 0.03으로 채운다. */
    private int warmUpWithSteadyChat(RealtimeSessionState state) {
        int t = 0;
        for (; t < 11; t++) {
            feedSteady(state, t);
            assertThat(state.tick(at(t), VIEWERS)).isEmpty();
        }
        return t;
    }

    private void feedSteady(RealtimeSessionState state, int t) {
        state.feedChats(at(t), List.of("u1", "u2", "u3"));
    }

    private void feedNewcomers(RealtimeSessionState state, int t, int count) {
        state.feedChats(at(t), java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "new-" + t + "-" + i)
                .toList());
    }

    private static Instant at(int seconds) {
        return START.plusSeconds(seconds);
    }
}

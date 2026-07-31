package com.example.laive.broadcast.realtime.domain.highlight;

import java.time.Instant;
import java.util.Optional;

/**
 * CHAT_SURGE 판정 상태기계다: IDLE → ACTIVE → (확정) → COOLDOWN → IDLE
 * (HIGHLIGHT_SPEC §3).
 *
 * <p>진입(z ≥ zEnter 연속 enterTicks)과 이탈(z ≤ zExit 연속 exitTicks)의 임계를
 * 다르게 두는 히스테리시스로, 경계에서 구간이 잘게 쪼개지는 것을 막는다.
 *
 * <p>스레드 안전하지 않다. 호출자(세션 상태)가 동기화한다.
 */
public class HighlightStateMachine {

    public enum Phase {
        IDLE, ACTIVE, COOLDOWN
    }

    private final HighlightParams params;

    private Phase phase = Phase.IDLE;
    private int consecutiveEnter;
    private int consecutiveExit;
    /** 이탈 연속 카운트의 첫 틱. 확정되면 이 시각이 구간의 끝이다. */
    private Instant exitFirstAt;
    /** 리드 소급을 이미 반영한 구간 시작점. */
    private Instant segmentStart;
    /** 진입이 확정된 틱 시각. maxActive 판정 기준이다. */
    private Instant activeSince;
    private Instant cooldownUntil;
    private double peakZ;
    private double peakEngagement;

    public HighlightStateMachine(HighlightParams params) {
        this.params = params;
    }

    public Phase phase() {
        return phase;
    }

    /** 정상 판정 틱을 반영한다. 구간이 확정되면 그 구간을 돌려준다. */
    public Optional<HighlightCandidate> onTick(Instant now, double z, double engagement) {
        return switch (phase) {
            case COOLDOWN -> {
                if (now.isBefore(cooldownUntil)) {
                    yield Optional.empty();
                }
                phase = Phase.IDLE;
                yield idleTick(now, z);
            }
            case IDLE -> idleTick(now, z);
            case ACTIVE -> activeTick(now, z, engagement);
        };
    }

    /**
     * 정보가 없어 판정하지 못하는 틱(워밍업·시청자 결측·수집 공백)을 반영한다.
     * 저활동 틱은 {@link #onQuietTick}으로 — 그쪽은 정보 부재가 아니라 신호다.
     *
     * <p>연속 카운트는 건드리지 않는다 — 판정 불가가 "진입/이탈 조건 미충족"으로
     * 해석되면 공백 한 번에 진행 중이던 진입이 리셋된다. 단 ACTIVE 구간의
     * 지속 시간 상한(maxActive)만은 계속 지켜, 판정 불가가 길어져도 구간이
     * 무한정 늘어나지 않게 한다.
     */
    public Optional<HighlightCandidate> onSkippedTick(Instant now) {
        if (phase == Phase.ACTIVE && !now.isBefore(activeSince.plus(params.maxActive()))) {
            return Optional.of(finalizeSegment(now, now));
        }
        return Optional.empty();
    }

    /**
     * 저활동 틱(가드 3 — 윈도우 채팅이 minWindowChats 미만)을 반영한다.
     *
     * <p>ACTIVE 중의 저활동은 "판정 불가"가 아니라 채팅이 식었다는 적극적
     * 신호이므로 이탈 연속 카운트에 산입한다. 이게 없으면 소규모 채널에서
     * 급증이 끝나도 판정 가능한 틱이 없어 구간이 maxActive까지 늘어진다.
     * IDLE·COOLDOWN에서는 {@link #onSkippedTick}과 같이 동결한다.
     *
     * <p>z·engagement를 받지 않는다 — 저활동 틱의 지표는 신뢰할 수 없다는 것이
     * 가드 3의 전제라, 피크 갱신에 쓰면 자기모순이다.
     */
    public Optional<HighlightCandidate> onQuietTick(Instant now) {
        if (phase != Phase.ACTIVE) {
            return Optional.empty();
        }
        if (!now.isBefore(activeSince.plus(params.maxActive()))) {
            return Optional.of(finalizeSegment(now, now));
        }
        return countExit(now);
    }

    /** 방송 종료 시 ACTIVE 구간을 종료 시각으로 강제 확정한다(가드 5). */
    public Optional<HighlightCandidate> forceFinalize(Instant endedAt) {
        if (phase != Phase.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(finalizeSegment(endedAt, endedAt));
    }

    private Optional<HighlightCandidate> idleTick(Instant now, double z) {
        if (z >= params.zEnter()) {
            consecutiveEnter++;
        } else {
            consecutiveEnter = 0;
        }
        if (consecutiveEnter < params.enterTicks()) {
            return Optional.empty();
        }

        phase = Phase.ACTIVE;
        activeSince = now;
        // 리드 소급: 채팅 반응은 원인보다 늦으므로 시작점을 앞으로 당긴다.
        segmentStart = now.minus(params.lead());
        consecutiveEnter = 0;
        consecutiveExit = 0;
        exitFirstAt = null;
        peakZ = z;
        peakEngagement = 0;
        return Optional.empty();
    }

    private Optional<HighlightCandidate> activeTick(Instant now, double z, double engagement) {
        peakZ = Math.max(peakZ, z);
        peakEngagement = Math.max(peakEngagement, engagement);

        // 지속 상한이 이탈 판정보다 먼저다. 계속 뜨거우면 이탈 조건이 영영
        // 충족되지 않아 이 분기만이 구간을 끝낼 수 있다.
        if (!now.isBefore(activeSince.plus(params.maxActive()))) {
            return Optional.of(finalizeSegment(now, now));
        }

        if (z <= params.zExit()) {
            return countExit(now);
        }
        consecutiveExit = 0;
        exitFirstAt = null;
        return Optional.empty();
    }

    /** 이탈 표 하나를 반영한다. 정상 이탈 틱과 저활동 틱이 같은 스트릭을 공유한다. */
    private Optional<HighlightCandidate> countExit(Instant now) {
        if (consecutiveExit == 0) {
            exitFirstAt = now;
        }
        consecutiveExit++;
        if (consecutiveExit >= params.exitTicks()) {
            // 끝은 이탈이 시작된 첫 틱이다. 확정 틱(now)으로 두면
            // 식은 뒤의 exitTicks초가 구간에 붙는다.
            return Optional.of(finalizeSegment(exitFirstAt, now));
        }
        return Optional.empty();
    }

    private HighlightCandidate finalizeSegment(Instant endedAt, Instant now) {
        HighlightCandidate candidate =
                new HighlightCandidate(segmentStart, endedAt, peakZ, peakEngagement);
        phase = Phase.COOLDOWN;
        cooldownUntil = now.plus(params.cooldown());
        consecutiveEnter = 0;
        consecutiveExit = 0;
        exitFirstAt = null;
        segmentStart = null;
        activeSince = null;
        return candidate;
    }
}

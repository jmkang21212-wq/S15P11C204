package com.example.laive.broadcast.realtime.service;

import com.example.laive.broadcast.realtime.domain.highlight.BaselineBuffer;
import com.example.laive.broadcast.realtime.domain.highlight.EngagementWindow;
import com.example.laive.broadcast.realtime.domain.highlight.HighlightCandidate;
import com.example.laive.broadcast.realtime.domain.highlight.HighlightParams;
import com.example.laive.broadcast.realtime.domain.highlight.HighlightStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 방송 중인 세션 하나의 하이라이트 판정 상태다. 60초 윈도우, 15분 baseline,
 * 상태기계를 들고 매 틱 HIGHLIGHT_SPEC의 가드를 적용한다.
 *
 * <p>모든 접근은 인스턴스 동기화로 보호한다. 채팅 공급(수집기 요청 스레드)과
 * 틱(전용 스케줄러 스레드)이 동시에 들어온다. 락은 세션당 하나라 경합은
 * 같은 세션의 채팅과 틱 사이에서만 생긴다.
 */
class RealtimeSessionState {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionState.class);

    private final long sessionId;
    private final Instant sessionStartedAt;
    private final HighlightParams params;
    private final EngagementWindow window;
    private final BaselineBuffer baseline;
    private final HighlightStateMachine machine;

    /** 마지막으로 chat-ai에 ingest를 보낸 시각. 틱이 빈 배치 보충 여부를 정한다. */
    private Instant lastIngestAt;
    /** 진행 중인 수집 공백(가드 9). */
    private boolean gapOpen;
    /** 공백이 닫힌 뒤에도 60초 윈도우가 공백에 걸치는 동안은 판정을 쉰다. */
    private Instant gapExcludedUntil;

    RealtimeSessionState(long sessionId, Instant sessionStartedAt, HighlightParams params) {
        this.sessionId = sessionId;
        this.sessionStartedAt = sessionStartedAt;
        this.params = params;
        this.window = new EngagementWindow();
        this.baseline = new BaselineBuffer(params.baselineSize());
        this.machine = new HighlightStateMachine(params);
    }

    long sessionId() {
        return sessionId;
    }

    Instant sessionStartedAt() {
        return sessionStartedAt;
    }

    /** 채팅을 판정 윈도우에 공급한다. receivedAt은 서버 수신 시각이다(가드 8). */
    synchronized void feedChats(Instant receivedAt, List<String> uids) {
        for (String uid : uids) {
            window.add(receivedAt, uid);
        }
    }

    synchronized void markIngest(Instant at) {
        lastIngestAt = at;
    }

    synchronized boolean needsIngest(Instant now, java.time.Duration interval) {
        return lastIngestAt == null || !lastIngestAt.plus(interval).isAfter(now);
    }

    /** 수집 공백 보고를 반영한다. endedAt이 null이면 공백이 진행 중이다. */
    synchronized void onGap(Instant endedAt) {
        if (endedAt == null) {
            gapOpen = true;
            return;
        }
        gapOpen = false;
        gapExcludedUntil = endedAt.plus(params.engageWindow());
    }

    /**
     * 1초 틱을 판정한다. 구간이 확정되면 돌려준다.
     *
     * @param viewers 최신 시청자 수. null이면 판정을 쉰다(가드 4)
     */
    synchronized Optional<HighlightCandidate> tick(Instant now, Integer viewers) {
        window.evictBefore(now.minus(params.engageWindow()));

        // 가드 9: 공백에 걸친 틱은 판정도 baseline도 제외한다. 공백은 튀는 값
        // 하나가 아니라 baseline 전체를 끌어내리는 구간이라 median/MAD의
        // 강건함이 막아주지 못하고, 복구 직후 정상 채팅이 급증으로 오탐된다.
        if (gapOpen || (gapExcludedUntil != null && now.isBefore(gapExcludedUntil))) {
            return skipped(now, "gap");
        }
        // 가드 4: 시청자 수 없이 engagement를 계산할 수 없다. 0으로 나누지 않는다.
        if (viewers == null || viewers <= 0) {
            return skipped(now, "no-viewers");
        }

        double engagement = (double) window.uniqueChatters() / viewers;

        // 가드 1: 워밍업. 인사 러시가 baseline 없이 판정되면 방송 첫 3분이
        // 매번 하이라이트가 된다. baseline 적재는 계속한다 — 워밍업의 목적이
        // baseline을 채우는 것이다.
        if (now.isBefore(sessionStartedAt.plus(params.warmup()))) {
            baseline.add(engagement);
            return skipped(now, "warmup");
        }
        // 가드 7: 재시작 직후 등 baseline이 비어 있으면 z를 계산할 수 없다.
        if (baseline.size() == 0) {
            baseline.add(engagement);
            return skipped(now, "empty-baseline");
        }

        // z는 현재 값을 넣기 전의 baseline으로 계산한다. 자기 자신이 포함되면
        // 급증 값이 median을 끌어올려 z가 눌린다.
        double z = baseline.zScore(engagement, params.madEpsilon());

        Optional<HighlightCandidate> confirmed;
        if (window.chatCount() < params.minWindowChats()) {
            // 가드 3: 저활동 틱은 판정하지 않는다. 채팅 몇 건으로 계산한
            // engagement는 분모(시청자)만 흔들려도 z가 널뛴다.
            confirmed = machine.onSkippedTick(now);
        } else {
            confirmed = machine.onTick(now, z, engagement);
        }
        baseline.add(engagement);

        // 파라미터가 백테스트 미검증이라 매 틱을 남긴다. 실방송 로그가 튜닝 근거다.
        if (log.isDebugEnabled()) {
            log.debug("highlight tick: sessionId={} ts={} chats={} uniq={} viewers={} "
                            + "engagement={} z={} phase={}",
                    sessionId, now, window.chatCount(), window.uniqueChatters(), viewers,
                    String.format("%.4f", engagement), String.format("%.2f", z), machine.phase());
        }
        return confirmed;
    }

    /** 방송 종료 시 ACTIVE 구간을 강제 확정한다(가드 5). */
    synchronized Optional<HighlightCandidate> forceFinalize(Instant endedAt) {
        return machine.forceFinalize(endedAt);
    }

    private Optional<HighlightCandidate> skipped(Instant now, String reason) {
        if (log.isDebugEnabled()) {
            log.debug("highlight tick skipped: sessionId={} ts={} reason={} phase={}",
                    sessionId, now, reason, machine.phase());
        }
        return machine.onSkippedTick(now);
    }
}

package com.example.laive.broadcast.realtime.service;

import com.example.laive.broadcast.realtime.config.HighlightProperties;
import com.example.laive.broadcast.realtime.domain.highlight.HighlightCandidate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방송 중 세션의 인메모리 실시간 상태(시청자 수 + 하이라이트 판정)를 든다.
 *
 * <p>Redis가 아니라 인메모리인 이유: 단일 인스턴스 전제에 서버 재시작 시 리셋이
 * 허용되는 값들이다(HIGHLIGHT_SPEC 가드 7 — 재시작하면 baseline이 비어 워밍업부터
 * 다시 시작한다). 재시작 복구는 다음 채팅 배치의 lazy 재등록이 맡는다.
 */
@Component
public class RealtimeSessionRegistry {

    /**
     * 시청자 수를 이 시간 넘게 못 받으면 모른다고 답한다(HIGHLIGHT_SPEC 가드 4).
     * 죽은 값으로 engagement를 계산하면 시청자가 빠져나간 뒤의 채팅 급증이
     * 실제보다 크게 잡힌다.
     */
    private static final Duration VIEWER_STALENESS = Duration.ofMinutes(5);

    private final ConcurrentHashMap<Long, ViewerSample> viewersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RealtimeSessionState> statesBySession = new ConcurrentHashMap<>();
    private final HighlightProperties highlightProperties;
    private final Clock clock;

    public RealtimeSessionRegistry(HighlightProperties highlightProperties, Clock clock) {
        this.highlightProperties = highlightProperties;
        this.clock = clock;
    }

    /** 판정 대상 세션을 등록한다. 이미 등록돼 있으면 기존 상태를 유지한다. */
    public void register(long sessionId, Instant sessionStartedAt) {
        statesBySession.computeIfAbsent(sessionId, id ->
                new RealtimeSessionState(id, sessionStartedAt, highlightProperties.toParams()));
    }

    public boolean isRegistered(long sessionId) {
        return statesBySession.containsKey(sessionId);
    }

    /** 채팅을 판정 윈도우에 공급한다. 미등록 세션이면 조용히 무시한다. */
    public void feedChats(long sessionId, Instant receivedAt, List<String> uids) {
        RealtimeSessionState state = statesBySession.get(sessionId);
        if (state != null) {
            state.feedChats(receivedAt, uids);
        }
    }

    /** chat-ai ingest를 보냈음을 기록한다. 틱의 빈 배치 보충이 이 시각을 본다. */
    public void markIngest(long sessionId, Instant at) {
        RealtimeSessionState state = statesBySession.get(sessionId);
        if (state != null) {
            state.markIngest(at);
        }
    }

    /** 수집 공백 보고를 판정기에 반영한다(가드 9). */
    public void onGap(long sessionId, Instant gapEndedAt) {
        RealtimeSessionState state = statesBySession.get(sessionId);
        if (state != null) {
            state.onGap(gapEndedAt);
        }
    }

    Collection<RealtimeSessionState> states() {
        return statesBySession.values();
    }

    /** 시청자 수 측정을 반영한다. 재전송으로 과거 배치가 늦게 와도 최신 측정만 남긴다. */
    public void recordViewers(long sessionId, int viewerCount, Instant measuredAt) {
        viewersBySession.merge(
                sessionId,
                new ViewerSample(viewerCount, measuredAt),
                (existing, candidate) ->
                        existing.measuredAt().isAfter(candidate.measuredAt()) ? existing : candidate
        );
    }

    /** 최신 시청자 수를 반환한다. 없거나 5분 넘게 오래됐으면 null이다. */
    public Integer currentViewers(long sessionId) {
        ViewerSample sample = viewersBySession.get(sessionId);
        if (sample == null) {
            return null;
        }
        if (sample.measuredAt().plus(VIEWER_STALENESS).isBefore(clock.instant())) {
            return null;
        }
        return sample.viewerCount();
    }

    /**
     * 세션 종료 시 ACTIVE 구간을 강제 확정하고 인메모리 상태를 정리한다.
     *
     * <p>세션 시작 시각을 함께 돌려준다 — 이 시점에는 세션이 이미 ENDED로
     * 전이된 뒤라 DB의 활성 세션 조회로는 오프셋(start_ms) 기준을 얻을 수 없다.
     */
    public Optional<FinalizedHighlight> finalizeAndRemove(long sessionId, Instant endedAt) {
        viewersBySession.remove(sessionId);
        RealtimeSessionState state = statesBySession.remove(sessionId);
        if (state == null) {
            return Optional.empty();
        }
        return state.forceFinalize(endedAt)
                .map(candidate -> new FinalizedHighlight(state.sessionStartedAt(), candidate));
    }

    /** 종료 시 강제 확정된 구간과 오프셋 계산에 필요한 세션 시작 시각이다. */
    public record FinalizedHighlight(Instant sessionStartedAt, HighlightCandidate candidate) {
    }

    private record ViewerSample(int viewerCount, Instant measuredAt) {
    }
}

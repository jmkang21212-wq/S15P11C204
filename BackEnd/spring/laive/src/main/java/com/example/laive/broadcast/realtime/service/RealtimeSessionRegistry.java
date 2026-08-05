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
     *
     * <p>기준 시각은 measuredAt(수집기 시계)이 아니라 서버 수신 시각이다(가드 8 —
     * 클라이언트 시계를 신뢰하지 않는다). measuredAt으로 재면 수집기와 서버의
     * 시계가 5분 이상 어긋났을 때(Docker VM 드리프트 등) 10초마다 멀쩡히 도착하는
     * 값이 전부 "오래됐다"로 버려져, 판정기만 no-viewers에 갇힌다 — 신선도 검사가
     * 없는 콘솔·대시보드 표시는 정상이라 원인을 찾기 어려운 증상이 된다.
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

    /**
     * 판정기의 마지막 틱 상태다(관측 전용 — 콘솔 스냅샷이 쓴다).
     * 미등록 세션(방송 전·종료 후·재시작 직후 lazy 재등록 전)이면 비어 있다.
     */
    public Optional<HighlightTickView> highlightView(long sessionId) {
        return Optional.ofNullable(statesBySession.get(sessionId))
                .map(RealtimeSessionState::view);
    }

    /**
     * 시청자 수 측정을 반영한다. 재전송으로 과거 배치가 늦게 와도 최신 측정만
     * 남긴다 — 이 "어느 쪽이 최신인가" 비교는 같은 수집기 시계끼리라 measuredAt이
     * 맞고, 신선도(수신이 끊겼는가)만 서버 시계로 잰다.
     */
    public void recordViewers(long sessionId, int viewerCount, Instant measuredAt) {
        viewersBySession.merge(
                sessionId,
                new ViewerSample(viewerCount, measuredAt, clock.instant()),
                (existing, candidate) ->
                        existing.measuredAt().isAfter(candidate.measuredAt()) ? existing : candidate
        );
    }

    /** 최신 시청자 수를 반환한다. 없거나 수신이 5분 넘게 끊겼으면 null이다. */
    public Integer currentViewers(long sessionId) {
        ViewerSample sample = viewersBySession.get(sessionId);
        if (sample == null) {
            return null;
        }
        if (sample.receivedAt().plus(VIEWER_STALENESS).isBefore(clock.instant())) {
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

    /** @param receivedAt 서버 수신 시각 — 신선도 검사 기준(가드 8). measuredAt은 수집기 시계다 */
    private record ViewerSample(int viewerCount, Instant measuredAt, Instant receivedAt) {
    }
}

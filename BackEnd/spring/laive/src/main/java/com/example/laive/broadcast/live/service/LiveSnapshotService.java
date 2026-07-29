package com.example.laive.broadcast.live.service;

import com.example.laive.broadcast.collect.integration.CollectorStatusStore;
import com.example.laive.broadcast.live.dto.response.LiveSnapshotResponse;
import com.example.laive.broadcast.live.repository.mybatis.LiveSnapshotMapper;
import com.example.laive.broadcast.realtime.domain.SentimentWindow;
import com.example.laive.broadcast.realtime.integration.SentimentSnapshotStore;
import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.example.laive.broadcast.session.service.BroadcastSessionQueryService;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 실시간 콘솔 스냅샷을 취합한다(명세 10.6). 프론트가 2~3초 주기로 폴링한다.
 *
 * <p>구성 요소별 출처 — 세션은 DB(정본), 시청자 수·여론은 Redis 상태 Hash(방송 중
 * 현재값), 수집기 상태는 하트비트 기반 {@link CollectorStatusStore}. 세션 조회가
 * 소유권 검증을 겸하므로 타인 세션은 404로 숨겨진다.
 */
@Service
public class LiveSnapshotService {

    /** chat-ai 윈도우 기본 길이. 여론 스냅샷이 아직 없을 때의 표기용이다. */
    private static final int DEFAULT_WINDOW_SEC = 180;

    /**
     * 여론 스냅샷이 이 시간 안에 갱신됐으면 AI 파이프라인을 RUNNING으로 본다.
     * 방송 중에는 틱이 매초 ingest를 보내므로 정상일 때 항상 신선하다.
     */
    private static final Duration AI_FRESHNESS = Duration.ofSeconds(10);

    private final BroadcastSessionQueryService sessionQueryService;
    private final CollectorStatusStore collectorStatusStore;
    private final SentimentSnapshotStore sentimentSnapshotStore;
    private final LiveSnapshotMapper liveSnapshotMapper;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public LiveSnapshotService(
            BroadcastSessionQueryService sessionQueryService,
            CollectorStatusStore collectorStatusStore,
            SentimentSnapshotStore sentimentSnapshotStore,
            LiveSnapshotMapper liveSnapshotMapper,
            StringRedisTemplate redisTemplate,
            Clock clock
    ) {
        this.sessionQueryService = sessionQueryService;
        this.collectorStatusStore = collectorStatusStore;
        this.sentimentSnapshotStore = sentimentSnapshotStore;
        this.liveSnapshotMapper = liveSnapshotMapper;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public LiveSnapshotResponse getSnapshot(long streamerId, long sessionId) {
        BroadcastSessionDetail detail = sessionQueryService.getSession(streamerId, sessionId);
        Instant now = clock.instant();

        return new LiveSnapshotResponse(
                session(detail, now),
                services(sessionId, now),
                List.of(),
                sentiment(sessionId),
                List.of(),
                lastEventId(sessionId)
        );
    }

    private LiveSnapshotResponse.Session session(BroadcastSessionDetail detail, Instant now) {
        Instant startedAt = detail.startedAt();
        Instant until = detail.endedAt() != null ? detail.endedAt() : now;
        long elapsedSeconds = startedAt == null
                ? 0
                : Math.max(0, Duration.between(startedAt, until).toSeconds());

        int currentViewers = sentimentSnapshotStore.readViewers(detail.sessionId())
                .or(() -> liveSnapshotMapper.findLatestViewerCount(detail.sessionId()))
                .orElse(0);

        return new LiveSnapshotResponse.Session(
                detail.sessionId(),
                detail.status().name(),
                startedAt,
                elapsedSeconds,
                currentViewers
        );
    }

    private LiveSnapshotResponse.Services services(long sessionId, Instant now) {
        // 음성 파이프라인(마이크·STT)은 미구현이다. 있는 척하지 않는다.
        // TODO: STT 경로가 생기면 실제 상태로 교체한다.
        String ai = sentimentSnapshotStore.readSentimentRecordedAt(sessionId)
                .filter(recordedAt -> recordedAt.plus(AI_FRESHNESS).isAfter(now))
                .map(recordedAt -> "RUNNING")
                .orElse("STOPPED");
        return new LiveSnapshotResponse.Services(
                "DISCONNECTED",
                "STOPPED",
                ai,
                collectorStatusStore.read(sessionId).name()
        );
    }

    private LiveSnapshotResponse.Sentiment sentiment(long sessionId) {
        SentimentWindow window = sentimentSnapshotStore.readSentiment(sessionId)
                .orElseGet(() -> new SentimentWindow(DEFAULT_WINDOW_SEC, 0, null, null, null));
        return new LiveSnapshotResponse.Sentiment(
                window.windowSec(),
                window.analyzedChat(),
                window.engagement(),
                window.dominant(),
                window.distribution()
        );
    }

    /**
     * 세션 이벤트 Stream의 최신 ID다. 이벤트가 없으면 "0-0"(명세 10.6).
     *
     * <p>폴링 전환으로 지금은 소비자가 없지만, 명세 스키마 유지 비용이 조회 한 번이라
     * 남겨둔다 — WebSocket을 다시 붙일 때 프론트 스키마가 바뀌지 않는다.
     */
    private String lastEventId(long sessionId) {
        List<MapRecord<String, Object, Object>> latest = redisTemplate.opsForStream()
                .reverseRange(SessionRedisGateway.streamKey(sessionId),
                        org.springframework.data.domain.Range.unbounded(),
                        org.springframework.data.redis.connection.Limit.limit().count(1));
        if (latest == null || latest.isEmpty()) {
            return "0-0";
        }
        return latest.getFirst().getId().getValue();
    }
}

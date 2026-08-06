package com.example.laive.broadcast.live.service;

import com.example.laive.broadcast.collect.integration.CollectorStatusStore;
import com.example.laive.broadcast.fandom.domain.TopViewer;
import com.example.laive.broadcast.fandom.repository.mybatis.ViewerScoreMapper;
import com.example.laive.broadcast.live.dto.response.LiveSnapshotResponse;
import com.example.laive.broadcast.live.repository.mybatis.LiveSnapshotMapper;
import com.example.laive.broadcast.questions.config.RepeatedQuestionProperties;
import com.example.laive.broadcast.questions.repository.mybatis.RepeatedQuestionMapper;
import com.example.laive.broadcast.realtime.domain.SentimentWindow;
import com.example.laive.broadcast.realtime.integration.SentimentSnapshotStore;
import com.example.laive.broadcast.realtime.repository.mybatis.TriggerMapper;
import com.example.laive.broadcast.realtime.service.HighlightTickView;
import com.example.laive.broadcast.realtime.service.RealtimeSessionRegistry;
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
     * 브리핑 피드 최대 건수. 콘솔은 최근 흐름만 보여주면 되고, 전체 이력은
     * 세션 리포트 몫이다.
     */
    private static final int BRIEFING_LIMIT = 20;

    /**
     * 여론 스냅샷이 이 시간 안에 갱신됐으면 AI 파이프라인을 RUNNING으로 본다.
     * 방송 중에는 틱이 매초 ingest를 보내므로 정상일 때 항상 신선하다.
     */
    private static final Duration AI_FRESHNESS = Duration.ofSeconds(10);

    /** 열성 시청자 노출 수(FR-F-03). 콘솔 카드가 다섯 줄 고정이다. */
    private static final int TOP_VIEWER_LIMIT = 5;

    private final BroadcastSessionQueryService sessionQueryService;
    private final CollectorStatusStore collectorStatusStore;
    private final SentimentSnapshotStore sentimentSnapshotStore;
    private final LiveSnapshotMapper liveSnapshotMapper;
    private final RepeatedQuestionMapper repeatedQuestionMapper;
    private final RepeatedQuestionProperties repeatedQuestionProperties;
    private final ViewerScoreMapper viewerScoreMapper;
    private final RealtimeSessionRegistry realtimeSessionRegistry;
    private final TriggerMapper triggerMapper;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public LiveSnapshotService(
            BroadcastSessionQueryService sessionQueryService,
            CollectorStatusStore collectorStatusStore,
            SentimentSnapshotStore sentimentSnapshotStore,
            LiveSnapshotMapper liveSnapshotMapper,
            RepeatedQuestionMapper repeatedQuestionMapper,
            RepeatedQuestionProperties repeatedQuestionProperties,
            ViewerScoreMapper viewerScoreMapper,
            RealtimeSessionRegistry realtimeSessionRegistry,
            TriggerMapper triggerMapper,
            StringRedisTemplate redisTemplate,
            Clock clock
    ) {
        this.sessionQueryService = sessionQueryService;
        this.collectorStatusStore = collectorStatusStore;
        this.sentimentSnapshotStore = sentimentSnapshotStore;
        this.liveSnapshotMapper = liveSnapshotMapper;
        this.repeatedQuestionMapper = repeatedQuestionMapper;
        this.repeatedQuestionProperties = repeatedQuestionProperties;
        this.viewerScoreMapper = viewerScoreMapper;
        this.realtimeSessionRegistry = realtimeSessionRegistry;
        this.triggerMapper = triggerMapper;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public LiveSnapshotResponse getSnapshot(long streamerId, long sessionId) {
        BroadcastSessionDetail detail = sessionQueryService.getSession(streamerId, sessionId);
        Instant now = clock.instant();

        return new LiveSnapshotResponse(
                session(detail, now),
                services(sessionId, now),
                briefings(sessionId),
                sentiment(sessionId),
                highlight(sessionId),
                repeatedQuestions(sessionId),
                topViewers(sessionId),
                lastEventId(sessionId)
        );
    }

    /**
     * CHAT_SURGE 판정기의 현재 상태 + 확정 누계다(관측 전용). 실시간 값(z·phase)은
     * 인메모리 판정기에서, 확정 누계는 {@code triggers}에서 온다 — 판정기는 재시작
     * 시 리셋되지만 확정된 구간은 DB에 남아 있어 출처가 다르다.
     */
    private LiveSnapshotResponse.Highlight highlight(long sessionId) {
        HighlightTickView view = realtimeSessionRegistry.highlightView(sessionId).orElse(null);
        return new LiveSnapshotResponse.Highlight(
                view != null ? view.phase() : null,
                view != null ? view.z() : null,
                view != null ? view.zEnter() : null,
                view != null ? view.engagement() : null,
                view != null ? view.windowChats() : null,
                view != null ? view.uniqueChatters() : null,
                view != null ? view.skipReason() : null,
                view != null ? view.warmupUntil() : null,
                view != null ? view.engageWindowSec() : null,
                triggerMapper.countChatSurgeTriggers(sessionId),
                triggerMapper.findLastChatSurgeEndedAt(sessionId).orElse(null)
        );
    }

    /**
     * 최근 브리핑 피드다. AI 분석(RISK·PROMISE)과 여론 요약(CHAT_SUMMARY)이
     * live_briefings에 쌓이고, 여기서 최신순 최대 {@value BRIEFING_LIMIT}건을
     * 내려보낸다.
     */
    private List<LiveSnapshotResponse.Briefing> briefings(long sessionId) {
        return liveSnapshotMapper.findRecentBriefings(sessionId, BRIEFING_LIMIT).stream()
                .map(row -> new LiveSnapshotResponse.Briefing(
                        row.briefingId(),
                        row.briefingType(),
                        row.message(),
                        row.promiseId(),
                        row.createdAt()
                ))
                .toList();
    }

    /**
     * 노출 중(dismissed 아님) 질문을 askers 내림차순으로 저장 전량(≤50) 내려보낸다.
     * 상위 N 컷과 "FAQ만" 필터는 프론트 몫이다 — FAQ는 소수라 서버가 5개로 자르면
     * 필터 후 화면이 빈다(계약 §3의 limit=30 권고와 같은 이유).
     */
    private List<LiveSnapshotResponse.RepeatedQuestion> repeatedQuestions(long sessionId) {
        return repeatedQuestionMapper.findVisible(sessionId, repeatedQuestionProperties.limit()).stream()
                .map(view -> new LiveSnapshotResponse.RepeatedQuestion(
                        view.questionId(),
                        view.text(),
                        view.type(),
                        view.askers(),
                        view.messages(),
                        view.reasked(),
                        view.firstAskedAt(),
                        view.lastAskedAt()
                ))
                .toList();
    }

    /**
     * 열성 시청자 상위 {@value TOP_VIEWER_LIMIT}명이다 (FR-F-03). 도배 방지 산식은
     * ViewerScoreMapper.xml이 단일 원천이고, 스트리머 본인·매니저는 SQL에서 이미
     * 걸러져 있다. 닉네임이 아직 없는 시청자는 해시 파생 별칭으로 폴백한다 —
     * 해시 원문은 프론트로 보내지 않는다.
     */
    private List<LiveSnapshotResponse.TopViewer> topViewers(long sessionId) {
        return viewerScoreMapper.findTopViewers(sessionId, TOP_VIEWER_LIMIT).stream()
                .map(viewer -> new LiveSnapshotResponse.TopViewer(
                        viewer.viewerId(),
                        viewer.nickname(),
                        viewer.alias(),
                        viewer.chatScore(),
                        viewer.watchScore(),
                        viewer.totalScore()
                ))
                .toList();
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
        // 마이크·STT는 프론트 ↔ AI 음성 서버 직결(WS)이라 Spring이 관측할 수 없다.
        // 고정값을 내려보내고, 실제 상태 표시는 프론트가 자기 연결 상태로 한다.
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

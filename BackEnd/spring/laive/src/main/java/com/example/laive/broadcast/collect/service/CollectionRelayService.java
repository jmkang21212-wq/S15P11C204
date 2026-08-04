package com.example.laive.broadcast.collect.service;

import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import com.example.laive.broadcast.collect.domain.CollectionGap;
import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.IngestChat;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import com.example.laive.broadcast.realtime.integration.RealtimeSessionLifecycle;
import com.example.laive.broadcast.realtime.integration.SentimentSnapshotStore;
import com.example.laive.broadcast.realtime.service.RealtimeSessionRegistry;
import com.example.laive.broadcast.collect.repository.mybatis.PlatformSessionCollectionRef;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 수집기 배치를 chat-ai로 중계한 뒤 영속화한다(명세 13.6).
 *
 * <p>{@link CollectionIngestService}에 합치지 않은 이유: 저 서비스는 트랜잭션
 * 경계다. AI 호출을 그 안에 넣으면 2초 타임아웃 동안 커넥션을 물고, 예외가 새면
 * 원본 채팅 저장까지 롤백된다. 여기서는 트랜잭션 밖에서 AI를 먼저 부르고,
 * 무슨 일이 있어도 저장은 진행한다.
 *
 * <p>AI를 저장보다 먼저 부르는 이유: 응답의 results를 붙여 한 번의 INSERT로
 * 끝낸다. 저장 후 UPDATE 방식은 chat_messages에서 방금 넣은 행의 id를 돌려받는
 * 배관이 추가로 필요하다. 대신 INSERT가 실패해 수집기가 재전송하면 같은 배치가
 * chat-ai 윈도우에 두 번 반영될 수 있는데, 윈도우는 180초 여론 지표라 허용한다.
 */
@Service
public class CollectionRelayService {

    private final CollectionIngestService ingestService;
    private final ChatAiGateway chatAiGateway;
    private final RealtimeSessionRegistry registry;
    private final RealtimeSessionLifecycle realtimeLifecycle;
    private final SentimentSnapshotStore snapshotStore;
    private final Clock clock;

    public CollectionRelayService(
            CollectionIngestService ingestService,
            ChatAiGateway chatAiGateway,
            RealtimeSessionRegistry registry,
            RealtimeSessionLifecycle realtimeLifecycle,
            SentimentSnapshotStore snapshotStore,
            Clock clock
    ) {
        this.ingestService = ingestService;
        this.chatAiGateway = chatAiGateway;
        this.registry = registry;
        this.realtimeLifecycle = realtimeLifecycle;
        this.snapshotStore = snapshotStore;
        this.clock = clock;
    }

    /** 채팅 배치를 분류·저장하고 하이라이트 판정과 콘솔 스냅샷에 반영한다. */
    public BatchIngestResult appendChatMessages(long sessionId, List<ChatMessageItem> items) {
        // 서버 재시작 후 첫 배치가 판정을 되살린다(HIGHLIGHT_SPEC 가드 7).
        realtimeLifecycle.ensureRegistered(sessionId);

        List<IngestChat> chats = items.stream()
                .map(item -> new IngestChat(item.viewerHash(), item.message(), item.sentAt()))
                .toList();
        Optional<ChatAiIngestResult> classified =
                chatAiGateway.ingest(sessionId, registry.currentViewers(sessionId), chats);
        registry.markIngest(sessionId, clock.instant());

        BatchIngestResult result = ingestService.appendChatMessages(
                sessionId,
                classified.map(ai -> labeled(items, ai.labels())).orElse(items)
        );

        // 판정 윈도우 공급은 저장 성공 뒤에만 한다. 저장이 실패한 배치를 먹이면
        // 재시도 성공 시 같은 채팅이 두 번 세어진다. 시각은 서버 수신 시각이다
        // (가드 8 — sentAt은 치지직 클라이언트 시계라 판정에 쓰지 않는다).
        registry.feedChats(
                sessionId,
                clock.instant(),
                items.stream().map(ChatMessageItem::viewerHash).toList()
        );

        // 스냅샷도 저장 성공 뒤에만 남긴다. 저장이 실패하면 예외가 위로 올라가
        // 멱등 선점이 풀리고 수집기가 재전송한다 — 그 배치의 지표는 재전송이 남긴다.
        classified.map(ChatAiIngestResult::window)
                .ifPresent(window -> snapshotStore.recordSentiment(sessionId, window, clock.instant()));
        return result;
    }

    public BatchIngestResult appendPlatformChatMessages(
            long platformSessionId,
            List<ChatMessageItem> items
    ) {
        PlatformSessionCollectionRef source = ingestService.requirePlatformSession(platformSessionId);
        long sessionId = source.sessionId();
        realtimeLifecycle.ensureRegistered(sessionId);

        List<IngestChat> chats = items.stream()
                .map(item -> new IngestChat(item.viewerHash(), item.message(), item.sentAt()))
                .toList();
        Optional<ChatAiIngestResult> classified =
                chatAiGateway.ingest(sessionId, registry.currentViewers(sessionId), chats);
        registry.markIngest(sessionId, clock.instant());

        BatchIngestResult result = ingestService.appendPlatformChatMessages(
                platformSessionId,
                classified.map(ai -> labeled(items, ai.labels())).orElse(items)
        );
        registry.feedChats(
                sessionId,
                clock.instant(),
                items.stream().map(ChatMessageItem::viewerHash).toList()
        );
        classified.map(ChatAiIngestResult::window)
                .ifPresent(window -> snapshotStore.recordSentiment(sessionId, window, clock.instant()));
        return result;
    }

    /** 수집 공백 보고를 저장하고 판정기에도 알린다(가드 9). */
    public void recordCollectionGap(long sessionId, CollectionGap gap) {
        ingestService.recordCollectionGap(sessionId, gap);
        registry.onGap(sessionId, gap.endedAt());
    }

    public void recordPlatformCollectionGap(long platformSessionId, CollectionGap gap) {
        PlatformSessionCollectionRef source = ingestService.requirePlatformSession(platformSessionId);
        ingestService.recordPlatformCollectionGap(platformSessionId, gap);
        registry.onGap(source.sessionId(), gap.endedAt());
    }

    /** 시청자 수 배치를 저장하고 최신값을 판정기와 콘솔 스냅샷에 반영한다. */
    public BatchIngestResult appendViewerMetrics(long sessionId, List<ViewerMetricItem> items) {
        BatchIngestResult result = ingestService.appendViewerMetrics(sessionId, items);

        items.stream()
                .max(Comparator.comparing(ViewerMetricItem::measuredAt))
                .ifPresent(latest -> {
                    registry.recordViewers(sessionId, latest.viewerCount(), latest.measuredAt());
                    snapshotStore.recordViewers(sessionId, latest.viewerCount(), latest.measuredAt());
                });
        return result;
    }

    public BatchIngestResult appendPlatformViewerMetrics(
            long platformSessionId,
            List<ViewerMetricItem> items
    ) {
        PlatformSessionCollectionRef source = ingestService.requirePlatformSession(platformSessionId);
        BatchIngestResult result = ingestService.appendPlatformViewerMetrics(platformSessionId, items);
        items.stream()
                .max(Comparator.comparing(ViewerMetricItem::measuredAt))
                .ifPresent(latest -> {
                    Integer combined = ingestService.combinedViewerCount(
                            source.sessionId(), latest.measuredAt());
                    if (combined != null) {
                        registry.recordViewers(source.sessionId(), combined, latest.measuredAt());
                        snapshotStore.recordViewers(
                                source.sessionId(), combined, latest.measuredAt());
                    }
                });
        return result;
    }

    private static List<ChatMessageItem> labeled(List<ChatMessageItem> items, List<String> labels) {
        List<ChatMessageItem> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            String label = i < labels.size() ? labels.get(i) : null;
            result.add(label == null ? items.get(i) : items.get(i).withSentimentLabel(label));
        }
        return result;
    }
}

package com.example.laive.broadcast.realtime.service;

import com.example.laive.broadcast.realtime.config.HighlightProperties;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import com.example.laive.broadcast.realtime.integration.SentimentSnapshotStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 방송 중 세션마다 1초 틱을 돌린다: 하이라이트 판정 + 채팅이 없을 때의
 * 빈 배치 보충 전송(계약 — chat-ai 윈도우 감쇠와 콘솔 여론 갱신은 매초
 * ingest가 와야 이어진다).
 *
 * <p>{@code @Scheduled}가 아니라 전용 실행기인 이유: 앱 공용 스케줄러 풀은
 * 1개 스레드를 Outbox 디스패처가 쓰고 있다. 여기에 태우면 chat-ai의 2초
 * 타임아웃이 방송 준비·종료 이벤트 처리를 그만큼 밀어낸다.
 */
@Component
public class RealtimeTicker {

    private static final Logger log = LoggerFactory.getLogger(RealtimeTicker.class);

    private final RealtimeSessionRegistry registry;
    private final ChatAiGateway chatAiGateway;
    private final SentimentSnapshotStore snapshotStore;
    private final TriggerRecorder triggerRecorder;
    private final HighlightProperties properties;
    private final Clock clock;
    private final boolean enabled;

    private ScheduledExecutorService executor;

    public RealtimeTicker(
            RealtimeSessionRegistry registry,
            ChatAiGateway chatAiGateway,
            SentimentSnapshotStore snapshotStore,
            TriggerRecorder triggerRecorder,
            HighlightProperties properties,
            Clock clock,
            // 통합 테스트가 Outbox를 끄는 것과 같은 스위치로 틱도 끈다.
            @Value("${spring.task.scheduling.enabled:true}") boolean enabled
    ) {
        this.registry = registry;
        this.chatAiGateway = chatAiGateway;
        this.snapshotStore = snapshotStore;
        this.triggerRecorder = triggerRecorder;
        this.properties = properties;
        this.clock = clock;
        this.enabled = enabled;
    }

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info("실시간 틱이 비활성화되어 있습니다(spring.task.scheduling.enabled=false).");
            return;
        }
        long intervalMs = properties.tickInterval().toMillis();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "realtime-ticker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safeTick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** 예외가 새면 ScheduledExecutorService는 이후 실행을 조용히 멈춘다. 반드시 다 잡는다. */
    private void safeTick() {
        try {
            tick();
        } catch (RuntimeException exception) {
            log.error("실시간 틱 처리 실패", exception);
        }
    }

    private void tick() {
        for (RealtimeSessionState state : registry.states()) {
            try {
                tickSession(state);
            } catch (RuntimeException exception) {
                log.error("세션 틱 처리 실패: sessionId={}", state.sessionId(), exception);
            }
        }
    }

    private void tickSession(RealtimeSessionState state) {
        long sessionId = state.sessionId();
        Instant now = clock.instant();
        Integer viewers = registry.currentViewers(sessionId);

        // 채팅 배치가 이미 이번 주기에 ingest를 보냈으면 보충하지 않는다.
        if (state.needsIngest(now, properties.tickInterval())) {
            chatAiGateway.ingest(sessionId, viewers, List.of())
                    .ifPresent(result -> {
                        if (result.window() != null) {
                            snapshotStore.recordSentiment(sessionId, result.window(), now);
                        }
                    });
            state.markIngest(now);
        }

        state.tick(now, viewers)
                .ifPresent(candidate ->
                        triggerRecorder.record(sessionId, state.sessionStartedAt(), candidate));
    }
}

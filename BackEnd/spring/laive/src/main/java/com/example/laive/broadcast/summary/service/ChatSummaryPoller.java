package com.example.laive.broadcast.summary.service;

import com.example.laive.broadcast.summary.config.ChatSummaryProperties;
import com.example.laive.broadcast.summary.repository.mybatis.ChatSummaryMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 방송 중 세션의 여론 요약을 3분 주기로 생성한다(계약 §2의 AUTO).
 *
 * <p>{@code RepeatedQuestionPoller}와 같은 전용 실행기 구조다. 특히 요약은 LLM
 * 응답 대기가 최대 15초라 공용 스케줄러·1초 틱에 태울 수 없다 — 세션 하나의
 * 생성 대기가 다른 작업을 그만큼 밀어낸다.
 *
 * <p>폴링 대상을 DB에서 읽는 이유도 같다: 재시작 직후에도 폴링이 끊기지 않는다.
 */
@Component
public class ChatSummaryPoller {

    private static final Logger log = LoggerFactory.getLogger(ChatSummaryPoller.class);

    private final ChatSummaryMapper mapper;
    private final ChatSummaryPollService pollService;
    private final ChatSummaryProperties properties;
    private final boolean enabled;

    private ScheduledExecutorService executor;

    public ChatSummaryPoller(
            ChatSummaryMapper mapper,
            ChatSummaryPollService pollService,
            ChatSummaryProperties properties,
            // 통합 테스트가 Outbox·실시간 틱·질문 폴링을 끄는 것과 같은 스위치다.
            @Value("${spring.task.scheduling.enabled:true}") boolean enabled
    ) {
        this.mapper = mapper;
        this.pollService = pollService;
        this.properties = properties;
        this.enabled = enabled;
    }

    @PostConstruct
    void start() {
        if (!enabled) {
            log.info("여론 요약 폴링이 비활성화되어 있습니다(spring.task.scheduling.enabled=false).");
            return;
        }
        long intervalMs = properties.autoInterval().toMillis();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-summary-poller");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safePoll, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** 예외가 새면 ScheduledExecutorService는 이후 실행을 조용히 멈춘다. 반드시 다 잡는다. */
    private void safePoll() {
        try {
            poll();
        } catch (RuntimeException exception) {
            log.error("여론 요약 폴링 실패", exception);
        }
    }

    private void poll() {
        for (Long sessionId : mapper.findAutoSummarySessionIds()) {
            try {
                pollService.pollOnce(sessionId);
            } catch (RuntimeException exception) {
                // 한 세션의 실패가 다른 세션 요약을 막지 않게 한다.
                log.error("세션 여론 요약 폴링 실패: sessionId={}", sessionId, exception);
            }
        }
    }
}

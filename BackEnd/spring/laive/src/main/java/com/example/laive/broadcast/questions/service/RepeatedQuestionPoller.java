package com.example.laive.broadcast.questions.service;

import com.example.laive.broadcast.questions.config.RepeatedQuestionProperties;
import com.example.laive.broadcast.questions.repository.mybatis.RepeatedQuestionMapper;
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
 * 방송 중 세션의 반복 질문을 주기 폴링한다(계약 §3, 10~30초).
 *
 * <p>{@code RealtimeTicker}의 1초 루프에 태우지 않는 이유: 그 루프는 chat-ai
 * 2초 타임아웃이 이미 틱 예산을 잠식한다는 이유로 전용 실행기를 만들었다.
 * 여기에 질문 GET + 5문 DB 트랜잭션을 끼우면 같은 문제를 되살린다.
 *
 * <p>폴링 대상을 {@code RealtimeSessionRegistry}가 아니라 DB에서 읽는 이유:
 * 레지스트리는 재시작 후 첫 채팅 배치가 올 때까지 비어 있다. 15초에 한 번의
 * 인덱스 SELECT는 무시할 비용이고 재시작 직후에도 폴링이 끊기지 않는다.
 */
@Component
public class RepeatedQuestionPoller {

    private static final Logger log = LoggerFactory.getLogger(RepeatedQuestionPoller.class);

    private final RepeatedQuestionMapper mapper;
    private final RepeatedQuestionPollService pollService;
    private final RepeatedQuestionProperties properties;
    private final boolean enabled;

    private ScheduledExecutorService executor;

    public RepeatedQuestionPoller(
            RepeatedQuestionMapper mapper,
            RepeatedQuestionPollService pollService,
            RepeatedQuestionProperties properties,
            // 통합 테스트가 Outbox·실시간 틱을 끄는 것과 같은 스위치로 폴링도 끈다.
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
            log.info("반복 질문 폴링이 비활성화되어 있습니다(spring.task.scheduling.enabled=false).");
            return;
        }
        long intervalMs = properties.pollInterval().toMillis();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "repeated-question-poller");
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
            log.error("반복 질문 폴링 실패", exception);
        }
    }

    private void poll() {
        for (Long sessionId : mapper.findPollableSessionIds()) {
            try {
                pollService.pollOnce(sessionId);
            } catch (RuntimeException exception) {
                // 한 세션의 DB 반영 실패가 다른 세션 폴링을 막지 않게 한다.
                log.error("세션 반복 질문 폴링 실패: sessionId={}", sessionId, exception);
            }
        }
    }
}

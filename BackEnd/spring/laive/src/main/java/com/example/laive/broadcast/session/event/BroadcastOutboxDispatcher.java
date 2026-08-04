package com.example.laive.broadcast.session.event;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.event.handler.BroadcastOutboxEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 이벤트를 짧게 선점하고 외부 I/O는 DB 트랜잭션 밖에서 실행한다.
 */
@Component
public class BroadcastOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BroadcastOutboxDispatcher.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final BroadcastOutboxRepository outboxRepository;
    private final Map<BroadcastOutboxEventType, BroadcastOutboxEventHandler> handlers;
    private final BroadcastSessionProperties properties;
    private final Clock clock;
    private final String workerId = UUID.randomUUID().toString();

    public BroadcastOutboxDispatcher(
            BroadcastOutboxRepository outboxRepository,
            List<BroadcastOutboxEventHandler> handlers,
            BroadcastSessionProperties properties,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.handlers = indexHandlers(handlers);
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${laive.broadcast-session.outbox.poll-delay-ms:500}")
    public void dispatch() {
        Instant now = clock.instant();
        List<BroadcastOutboxEvent> events = outboxRepository.claim(
                workerId,
                now,
                now.plus(properties.outbox().lease()),
                properties.outbox().batchSize()
        );
        for (BroadcastOutboxEvent event : events) {
            process(event);
        }
    }

    private void process(BroadcastOutboxEvent event) {
        BroadcastOutboxEventHandler handler = handlers.get(event.eventType());
        if (handler == null) {
            retryOrDead(event, new IllegalStateException(
                    "등록되지 않은 이벤트 종류: " + event.eventType()
            ));
            return;
        }

        try {
            handler.handle(event);
            outboxRepository.markPublished(event.eventId(), clock.instant());
        } catch (Exception exception) {
            // 예외는 DB에 남기지 않으므로(sanitizedError 참고) 원인은 여기서만 볼 수 있다.
            log.warn(
                    "방송 Outbox 처리 실패: eventId={} type={} attempts={}",
                    event.eventId(),
                    event.eventType(),
                    event.attempts() + 1,
                    exception
            );
            retryOrDead(event, exception);
        }
    }

    private void retryOrDead(BroadcastOutboxEvent event, Exception exception) {
        int attempts = event.attempts() + 1;
        String error = sanitizedError(exception);
        if (attempts >= properties.outbox().maxAttempts()) {
            BroadcastOutboxEventHandler handler = handlers.get(event.eventType());
            if (handler != null) {
                try {
                    handler.onDead(event);
                } catch (Exception deadHandlerException) {
                    log.error(
                            "Outbox 최종 실패 보상 처리도 실패했습니다. eventId={}",
                            event.eventId(),
                            deadHandlerException
                    );
                }
            }
            outboxRepository.markDead(event.eventId(), attempts, error);
            return;
        }

        Duration backoff = backoff(attempts);
        outboxRepository.scheduleRetry(
                event.eventId(),
                attempts,
                clock.instant().plus(backoff),
                error
        );
    }

    private Duration backoff(int attempts) {
        long seconds = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration candidate = Duration.ofSeconds(seconds);
        return candidate.compareTo(properties.outbox().maxBackoff()) > 0
                ? properties.outbox().maxBackoff()
                : candidate;
    }

    private String sanitizedError(Exception exception) {
        // 토큰·채팅·STT 원문이 포함될 수 있는 예외 메시지는 저장하지 않는다.
        String value = exception.getClass().getName();
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private Map<BroadcastOutboxEventType, BroadcastOutboxEventHandler> indexHandlers(
            List<BroadcastOutboxEventHandler> handlerList
    ) {
        Map<BroadcastOutboxEventType, BroadcastOutboxEventHandler> result =
                new EnumMap<>(BroadcastOutboxEventType.class);
        for (BroadcastOutboxEventHandler handler : handlerList) {
            BroadcastOutboxEventHandler previous = result.put(handler.supports(), handler);
            if (previous != null) {
                throw new IllegalStateException("중복 Outbox handler: " + handler.supports());
            }
        }
        return Map.copyOf(result);
    }
}


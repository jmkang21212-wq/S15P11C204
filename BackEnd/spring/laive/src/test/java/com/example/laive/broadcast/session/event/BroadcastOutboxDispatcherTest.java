package com.example.laive.broadcast.session.event;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.event.handler.BroadcastOutboxEventHandler;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcastOutboxDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");

    @Test
    void publishesSuccessfullyHandledEvent() {
        BroadcastOutboxRepository repository = mock(BroadcastOutboxRepository.class);
        BroadcastOutboxEventHandler handler = handler();
        BroadcastOutboxEvent event = event(0);
        when(repository.claim(any(), eq(NOW), eq(NOW.plusSeconds(30)), eq(50)))
                .thenReturn(List.of(event));

        dispatcher(repository, handler, 8).dispatch();

        verify(handler).handle(event);
        verify(repository).markPublished(event.eventId(), NOW);
    }

    @Test
    void schedulesRetryWithoutPersistingSensitiveExceptionMessage() {
        BroadcastOutboxRepository repository = mock(BroadcastOutboxRepository.class);
        BroadcastOutboxEventHandler handler = handler();
        BroadcastOutboxEvent event = event(0);
        when(repository.claim(any(), eq(NOW), eq(NOW.plusSeconds(30)), eq(50)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("token=secret-value")).when(handler).handle(event);

        dispatcher(repository, handler, 8).dispatch();

        verify(repository).scheduleRetry(
                event.eventId(),
                1,
                NOW.plusSeconds(1),
                IllegalStateException.class.getName()
        );
    }

    @Test
    void marksDeadAndRunsCompensationAtMaxAttempts() {
        BroadcastOutboxRepository repository = mock(BroadcastOutboxRepository.class);
        BroadcastOutboxEventHandler handler = handler();
        BroadcastOutboxEvent event = event(7);
        when(repository.claim(any(), eq(NOW), eq(NOW.plusSeconds(30)), eq(50)))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("failed")).when(handler).handle(event);

        dispatcher(repository, handler, 8).dispatch();

        verify(handler).onDead(event);
        verify(repository).markDead(
                event.eventId(),
                8,
                IllegalStateException.class.getName()
        );
    }

    private BroadcastOutboxDispatcher dispatcher(
            BroadcastOutboxRepository repository,
            BroadcastOutboxEventHandler handler,
            int maxAttempts
    ) {
        BroadcastSessionProperties properties = new BroadcastSessionProperties(
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                new BroadcastSessionProperties.Outbox(
                        500,
                        50,
                        Duration.ofSeconds(30),
                        maxAttempts,
                        Duration.ofMinutes(5)
                ),
                new BroadcastSessionProperties.Ai(false, "http://localhost:8000", "")
        );
        return new BroadcastOutboxDispatcher(
                repository,
                List.of(handler),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private BroadcastOutboxEventHandler handler() {
        BroadcastOutboxEventHandler handler = mock(BroadcastOutboxEventHandler.class);
        when(handler.supports()).thenReturn(BroadcastOutboxEventType.BROADCAST_STARTED);
        return handler;
    }

    private BroadcastOutboxEvent event(int attempts) {
        return new BroadcastOutboxEvent(
                UUID.randomUUID(),
                9002L,
                BroadcastOutboxEventType.BROADCAST_STARTED,
                (short) 1,
                "broadcast-session:9002:started",
                "{}",
                attempts,
                NOW.minusSeconds(1)
        );
    }
}


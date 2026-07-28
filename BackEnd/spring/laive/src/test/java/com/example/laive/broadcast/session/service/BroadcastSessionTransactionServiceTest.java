package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.domain.BroadcastSession;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.broadcast.session.repository.BroadcastSessionRepository;
import com.example.laive.broadcast.session.repository.CalendarEventReference;
import com.example.laive.broadcast.session.repository.PlatformAccountReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcastSessionTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T12:00:00Z");
    private static final long STREAMER_ID = 101L;
    private static final long PLATFORM_ACCOUNT_ID = 15L;

    private BroadcastSessionRepository repository;
    private BroadcastOutboxRepository outboxRepository;
    private BroadcastSessionTransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(BroadcastSessionRepository.class);
        outboxRepository = mock(BroadcastOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new BroadcastSessionTransactionService(
                repository,
                outboxRepository,
                new OutboxEventFactory(objectMapper),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsPreparingSessionAndOutboxEvent() {
        when(repository.findOwnedPlatformAccount(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .thenReturn(Optional.of(connectedAccount()));
        when(repository.existsActive(STREAMER_ID)).thenReturn(false);
        when(repository.insert(any(BroadcastSession.class))).thenReturn(9002L);

        var result = service.start(STREAMER_ID, command(null));

        assertThat(result.sessionId()).isEqualTo(9002L);
        assertThat(result.status()).isEqualTo(BroadcastStatus.PREPARING);
        assertThat(result.startedAt()).isEqualTo(NOW);
        verify(outboxRepository).save(
                any(),
                eq(9002L),
                eq(com.example.laive.broadcast.session.event.BroadcastOutboxEventType.PREPARATION_REQUESTED),
                eq("broadcast-session:9002:prepare"),
                any(),
                eq(NOW)
        );
    }

    @Test
    void validatesCalendarAgainstPlatformAccount() {
        when(repository.findOwnedPlatformAccount(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .thenReturn(Optional.of(connectedAccount()));
        when(repository.findOwnedCalendarEvent(STREAMER_ID, 44L))
                .thenReturn(Optional.of(new CalendarEventReference(
                        44L,
                        STREAMER_ID,
                        99L,
                        "BROADCAST",
                        "SCHEDULED"
                )));

        assertThatThrownBy(() -> service.start(STREAMER_ID, command(44L)))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_STATE_CONFLICT");
        verify(repository, never()).insert(any());
    }

    @Test
    void rejectsExistingActiveSession() {
        when(repository.findOwnedPlatformAccount(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .thenReturn(Optional.of(connectedAccount()));
        when(repository.existsActive(STREAMER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.start(STREAMER_ID, command(null)))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("SESSION_ALREADY_LIVE");
    }

    @Test
    void endsSessionCreatesOneProcessingReportAndEvent() {
        BroadcastSession live = BroadcastSession.reconstitute(
                9002L,
                STREAMER_ID,
                PLATFORM_ACCOUNT_ID,
                44L,
                "방송",
                "FPS",
                "ko",
                null,
                null,
                BroadcastStatus.LIVE,
                NOW.minusSeconds(60),
                null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)
        );
        when(repository.findByIdForUpdate(STREAMER_ID, 9002L))
                .thenReturn(Optional.of(live));
        when(repository.markEnded(9002L, NOW)).thenReturn(1);
        when(repository.createProcessingReport(9002L)).thenReturn(301L);

        var result = service.end(STREAMER_ID, 9002L);

        assertThat(result.status()).isEqualTo(BroadcastStatus.ENDED);
        assertThat(result.reportStatus()).isEqualTo("PROCESSING");
        verify(repository).completeCalendarEvent(STREAMER_ID, 44L);
        verify(outboxRepository).save(
                any(),
                eq(9002L),
                eq(com.example.laive.broadcast.session.event.BroadcastOutboxEventType.BROADCAST_ENDED),
                eq("broadcast-session:9002:ended"),
                any(),
                eq(NOW)
        );
    }

    @Test
    void treatsMissingOwnedSessionAsNotFound() {
        when(repository.findByIdForUpdate(STREAMER_ID, 9002L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.end(STREAMER_ID, 9002L))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("SESSION_NOT_FOUND");
    }

    private PlatformAccountReference connectedAccount() {
        return new PlatformAccountReference(
                PLATFORM_ACCOUNT_ID,
                STREAMER_ID,
                "CHZZK",
                "CONNECTED"
        );
    }

    private StartBroadcastSessionCommand command(Long calendarEventId) {
        return new StartBroadcastSessionCommand(
                PLATFORM_ACCOUNT_ID,
                calendarEventId,
                "방송",
                "FPS",
                "ko"
        );
    }
}


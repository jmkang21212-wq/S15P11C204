package com.example.laive.broadcast.session.domain;

import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BroadcastSessionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-21T12:00:00Z");

    @Test
    void preparesWithNormalizedValuesAndDefaultLanguage() {
        BroadcastSession session = BroadcastSession.prepare(
                101L,
                15L,
                null,
                "  방송 제목  ",
                "  ",
                null,
                STARTED_AT
        );

        assertThat(session.status()).isEqualTo(BroadcastStatus.PREPARING);
        assertThat(session.title()).isEqualTo("방송 제목");
        assertThat(session.category()).isNull();
        assertThat(session.language()).isEqualTo("ko");
        assertThat(session.isActive()).isTrue();
        assertThat(session.endedAt()).isNull();
    }

    @Test
    void activatesOnlyPreparingSession() {
        BroadcastSession session = prepared();

        session.activate(STARTED_AT.plusSeconds(1));

        assertThat(session.status()).isEqualTo(BroadcastStatus.LIVE);
        // 이벤트 재전달은 멱등 처리한다.
        session.activate(STARTED_AT.plusSeconds(2));
        assertThat(session.status()).isEqualTo(BroadcastStatus.LIVE);
    }

    @Test
    void endsPreparingOrLiveSession() {
        BroadcastSession session = prepared();
        session.activate(STARTED_AT.plusSeconds(1));

        session.end(STARTED_AT.plusSeconds(60));

        assertThat(session.status()).isEqualTo(BroadcastStatus.ENDED);
        assertThat(session.endedAt()).isEqualTo(STARTED_AT.plusSeconds(60));
        assertThat(session.isTerminal()).isTrue();
    }

    @Test
    void rejectsRepeatedEndAndInvalidPeriod() {
        BroadcastSession session = prepared();

        assertThatThrownBy(() -> session.end(STARTED_AT.minusSeconds(1)))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("SESSION_INVALID_PERIOD");

        session.end(STARTED_AT.plusSeconds(1));
        assertThatThrownBy(() -> session.end(STARTED_AT.plusSeconds(2)))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("SESSION_ALREADY_ENDED");
    }

    @Test
    void failsActiveSessionWithTerminalTimestamp() {
        BroadcastSession session = prepared();

        session.fail(STARTED_AT.plusSeconds(5));

        assertThat(session.status()).isEqualTo(BroadcastStatus.FAILED);
        assertThat(session.endedAt()).isEqualTo(STARTED_AT.plusSeconds(5));
        assertThat(session.isTerminal()).isTrue();
    }

    private BroadcastSession prepared() {
        return BroadcastSession.prepare(
                101L,
                15L,
                44L,
                "방송",
                "FPS",
                "ko",
                STARTED_AT
        );
    }
}


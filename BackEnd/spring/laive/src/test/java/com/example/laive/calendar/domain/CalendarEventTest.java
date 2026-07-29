package com.example.laive.calendar.domain;

import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.common.api.PatchValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일정 불변조건과 부분 수정 병합 규칙을 검증한다.
 */
class CalendarEventTest {

    private static final long STREAMER_ID = 101L;
    private static final Instant START = Instant.parse("2026-07-20T12:00:00Z");
    private static final Instant END = Instant.parse("2026-07-20T15:00:00Z");

    private static UpdateCalendarEventCommand patch(
            PatchValue<Long> platformAccountId,
            PatchValue<CalendarEventType> eventType,
            PatchValue<String> title,
            PatchValue<String> memo,
            PatchValue<Instant> startAt,
            PatchValue<Instant> endAt,
            PatchValue<CalendarEventStatus> status
    ) {
        return new UpdateCalendarEventCommand(
                platformAccountId, eventType, title, memo, startAt, endAt, status
        );
    }

    private static UpdateCalendarEventCommand empty() {
        return patch(null, null, null, null, null, null, null);
    }

    private CalendarEvent broadcastEvent() {
        return CalendarEvent.schedule(STREAMER_ID, new CreateCalendarEventCommand(
                15L,
                CalendarEventType.BROADCAST,
                "발로란트 랭크 정복 Day 12",
                "Day 11 이어서",
                START,
                END
        )).withId(44L);
    }

    @Test
    void 새_일정은_SCHEDULED로_시작하고_제목_공백을_정리한다() {
        CalendarEvent event = CalendarEvent.schedule(STREAMER_ID, new CreateCalendarEventCommand(
                null,
                CalendarEventType.DAY_OFF,
                "  휴방  ",
                "   ",
                START,
                END
        ));

        assertThat(event.status()).isEqualTo(CalendarEventStatus.SCHEDULED);
        assertThat(event.title()).isEqualTo("휴방");
        assertThat(event.memo()).isNull();
        assertThat(event.platformAccountId()).isNull();
    }

    @Test
    void 종료가_시작보다_늦지_않으면_거부한다() {
        assertThatThrownBy(() -> CalendarEvent.schedule(STREAMER_ID, new CreateCalendarEventCommand(
                null, CalendarEventType.DAY_OFF, "휴방", null, END, START
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_INVALID_PERIOD");
    }

    @Test
    void BROADCAST는_플랫폼_계정이_없으면_거부한다() {
        assertThatThrownBy(() -> CalendarEvent.schedule(STREAMER_ID, new CreateCalendarEventCommand(
                null, CalendarEventType.BROADCAST, "방송", null, START, END
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_PLATFORM_REQUIRED");
    }

    @Test
    void 미전송_필드는_현재_값을_유지한다() {
        CalendarEvent updated = broadcastEvent().applyPatch(
                patch(null, null, PatchValue.of("제목만 변경"), null, null, null, null),
                false
        );

        assertThat(updated.title()).isEqualTo("제목만 변경");
        assertThat(updated.memo()).isEqualTo("Day 11 이어서");
        assertThat(updated.startAt()).isEqualTo(START);
        assertThat(updated.platformAccountId()).isEqualTo(15L);
        assertThat(updated.calendarEventId()).isEqualTo(44L);
    }

    @Test
    void 명시적_null은_메모를_비운다() {
        CalendarEvent updated = broadcastEvent().applyPatch(
                patch(null, null, null, PatchValue.of(null), null, null, null),
                false
        );

        assertThat(updated.memo()).isNull();
    }

    @Test
    void 취소는_상태_전이로_처리한다() {
        CalendarEvent updated = broadcastEvent().applyPatch(
                patch(null, null, null, null, null, null,
                        PatchValue.of(CalendarEventStatus.CANCELLED)),
                false
        );

        assertThat(updated.status()).isEqualTo(CalendarEventStatus.CANCELLED);
    }

    /** 각 필드는 멀쩡하지만 합치면 규칙을 어기는 요청도 걸러야 한다. */
    @Test
    void 유형만_BROADCAST로_바꾸면_플랫폼_계정_누락을_잡는다() {
        CalendarEvent dayOff = CalendarEvent.schedule(STREAMER_ID, new CreateCalendarEventCommand(
                null, CalendarEventType.DAY_OFF, "휴방", null, START, END
        ));

        assertThatThrownBy(() -> dayOff.applyPatch(
                patch(null, PatchValue.of(CalendarEventType.BROADCAST),
                        null, null, null, null, null),
                false
        ))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_PLATFORM_REQUIRED");
    }

    @Test
    void 세션에_연결된_일정은_시간을_바꿀_수_없다() {
        assertThatThrownBy(() -> broadcastEvent().applyPatch(
                patch(null, null, null, null,
                        PatchValue.of(START.plusSeconds(3600)), null, null),
                true
        ))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_LINKED_TO_SESSION");
    }

    @Test
    void 세션에_연결돼도_제목과_메모는_바꿀_수_있다() {
        CalendarEvent updated = broadcastEvent().applyPatch(
                patch(null, null, PatchValue.of("제목 변경"), PatchValue.of("메모 변경"),
                        null, null, null),
                true
        );

        assertThat(updated.title()).isEqualTo("제목 변경");
        assertThat(updated.memo()).isEqualTo("메모 변경");
    }

    /** 클라이언트가 전체 폼을 그대로 되돌려 보내도 제목 수정은 계속 되어야 한다. */
    @Test
    void 세션에_연결돼도_같은_값을_다시_보내면_변경으로_보지_않는다() {
        CalendarEvent updated = broadcastEvent().applyPatch(
                patch(PatchValue.of(15L), PatchValue.of(CalendarEventType.BROADCAST),
                        PatchValue.of("제목 변경"), null,
                        PatchValue.of(START), PatchValue.of(END), null),
                true
        );

        assertThat(updated.title()).isEqualTo("제목 변경");
    }

    @Test
    void 제목을_비우면_거부한다() {
        assertThatThrownBy(() -> broadcastEvent().applyPatch(
                patch(null, null, PatchValue.of("   "), null, null, null, null),
                false
        ))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_INVALID_TITLE");
    }
}

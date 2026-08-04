package com.example.laive.calendar.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.calendar.repository.PlatformAccountReference;
import com.example.laive.common.api.PatchValue;
import com.example.laive.promise.service.PromiseCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB 없이 Command Service의 검증 순서와 분기를 검증한다.
 *
 * 어떤 조건에서 플랫폼 계정을 다시 확인하는지, 갱신 결과를 어떻게 판정하는지는 SQL이 아니라
 * 서비스가 정하는 규칙이라 Repository를 대역으로 두고 확인한다.
 */
class CalendarEventCommandServiceTest {

    private static final long STREAMER_ID = 101L;
    private static final long EVENT_ID = 44L;
    private static final long PLATFORM_ACCOUNT_ID = 15L;
    private static final Instant START = Instant.parse("2026-07-20T12:00:00Z");
    private static final Instant END = Instant.parse("2026-07-20T15:00:00Z");

    private static final long PROMISE_ID = 7L;

    private CalendarEventRepository repository;
    private PromiseCommandService promiseCommandService;
    private CalendarEventCommandService service;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarEventRepository.class);
        promiseCommandService = mock(PromiseCommandService.class);
        service = new CalendarEventCommandService(repository, promiseCommandService);
    }

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

    private static CalendarEvent existingBroadcast() {
        return existingBroadcast(CalendarEventStatus.SCHEDULED, null);
    }

    private static CalendarEvent existingBroadcast(CalendarEventStatus status, Long promiseId) {
        return new CalendarEvent(
                EVENT_ID, STREAMER_ID, PLATFORM_ACCOUNT_ID,
                CalendarEventType.BROADCAST, "원래 제목", "원래 메모",
                START, END, status, promiseId
        );
    }

    private void givenConnectedAccount(long platformAccountId) {
        when(repository.findOwnedPlatformAccount(STREAMER_ID, platformAccountId))
                .thenReturn(Optional.of(new PlatformAccountReference(
                        platformAccountId, STREAMER_ID, "CONNECTED"
                )));
    }

    // ---------- create ----------

    @Test
    void 휴방은_플랫폼_계정을_조회하지_않는다() {
        when(repository.insert(any())).thenReturn(77L);

        CalendarEvent created = service.create(STREAMER_ID, new CreateCalendarEventCommand(
                null, CalendarEventType.DAY_OFF, "휴방", null, START, END
        ));

        assertThat(created.calendarEventId()).isEqualTo(77L);
        verify(repository, never()).findOwnedPlatformAccount(anyLong(), anyLong());
    }

    @Test
    void 생성은_INSERT가_발급한_ID를_붙여_반환한다() {
        givenConnectedAccount(PLATFORM_ACCOUNT_ID);
        when(repository.insert(any())).thenReturn(77L);

        CalendarEvent created = service.create(STREAMER_ID, new CreateCalendarEventCommand(
                PLATFORM_ACCOUNT_ID, CalendarEventType.BROADCAST, "방송", null, START, END
        ));

        assertThat(created.calendarEventId()).isEqualTo(77L);
        assertThat(created.status()).isEqualTo(CalendarEventStatus.SCHEDULED);
    }

    @Test
    void 소유하지_않은_플랫폼_계정이면_저장하지_않는다() {
        when(repository.findOwnedPlatformAccount(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(STREAMER_ID, new CreateCalendarEventCommand(
                PLATFORM_ACCOUNT_ID, CalendarEventType.BROADCAST, "방송", null, START, END
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");

        verify(repository, never()).insert(any());
    }

    @Test
    void 연결_상태가_아닌_플랫폼_계정이면_저장하지_않는다() {
        when(repository.findOwnedPlatformAccount(STREAMER_ID, PLATFORM_ACCOUNT_ID))
                .thenReturn(Optional.of(new PlatformAccountReference(
                        PLATFORM_ACCOUNT_ID, STREAMER_ID, "REAUTH_REQUIRED"
                )));

        assertThatThrownBy(() -> service.create(STREAMER_ID, new CreateCalendarEventCommand(
                PLATFORM_ACCOUNT_ID, CalendarEventType.BROADCAST, "방송", null, START, END
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_CONNECTED");

        verify(repository, never()).insert(any());
    }

    // ---------- update ----------

    @Test
    void 없는_일정을_수정하면_404다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, PatchValue.of("제목"), null, null, null, null
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_NOT_FOUND");

        verify(repository, never()).update(any());
    }

    /**
     * 플랫폼 계정을 그대로 두는 수정은 계정 상태를 다시 묻지 않는다.
     * 이 분기가 없으면 연동이 끊긴 뒤에는 그 일정을 취소할 수조차 없다.
     */
    @Test
    void 플랫폼_계정이_그대로면_계정_상태를_다시_확인하지_않는다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast()));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.update(any())).thenReturn(1);

        CalendarEvent updated = service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, null, null, null, null, PatchValue.of(CalendarEventStatus.CANCELLED)
        ));

        assertThat(updated.status()).isEqualTo(CalendarEventStatus.CANCELLED);
        verify(repository, never()).findOwnedPlatformAccount(anyLong(), anyLong());
    }

    @Test
    void 플랫폼_계정을_바꾸면_새_계정을_검증한다() {
        long newAccountId = 16L;
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast()));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.findOwnedPlatformAccount(STREAMER_ID, newAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(STREAMER_ID, EVENT_ID, patch(
                PatchValue.of(newAccountId), null, null, null, null, null, null
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");

        verify(repository, never()).update(any());
    }

    @Test
    void 세션_연결_여부를_조회해_수정_잠금에_반영한다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast()));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, null, null, PatchValue.of(START.plusSeconds(3600)), null, null
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_LINKED_TO_SESSION");

        verify(repository, never()).update(any());
    }

    // ---------- 취소 → 약속 복귀 ----------

    /** 취소하지 않으면 약속을 건드리지 않는다. 제목만 바꿨는데 약속이 되돌아가면 안 된다. */
    @Test
    void 취소가_아닌_수정은_연결된_약속을_건드리지_않는다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast(CalendarEventStatus.SCHEDULED, PROMISE_ID)));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.update(any())).thenReturn(1);

        service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, PatchValue.of("제목만 변경"), null, null, null, null
        ));

        verify(promiseCommandService, never()).revertScheduled(anyLong(), anyLong());
    }

    @Test
    void 약속에서_전환된_일정을_취소하면_약속을_되돌린다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast(CalendarEventStatus.SCHEDULED, PROMISE_ID)));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.update(any())).thenReturn(1);

        service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, null, null, null, null, PatchValue.of(CalendarEventStatus.CANCELLED)
        ));

        verify(promiseCommandService).revertScheduled(STREAMER_ID, PROMISE_ID);
    }

    /** 직접 만든 일정은 되돌릴 약속이 없다. */
    @Test
    void 약속과_무관한_일정_취소는_약속을_찾지_않는다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast(CalendarEventStatus.SCHEDULED, null)));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.update(any())).thenReturn(1);

        service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, null, null, null, null, PatchValue.of(CalendarEventStatus.CANCELLED)
        ));

        verify(promiseCommandService, never()).revertScheduled(anyLong(), anyLong());
    }

    /**
     * 이미 취소된 일정에 같은 요청이 다시 오는 것은 전이가 아니다.
     *
     * <p>이 구분이 없으면 재전송이 약속을 또 되돌리려 한다. 취소 후 사용자가 약속을 다른
     * 날짜로 재확정했다면 그 SCHEDULED를 PENDING으로 깎아내리게 된다.
     */
    @Test
    void 이미_취소된_일정에_같은_요청이_와도_약속을_다시_되돌리지_않는다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast(CalendarEventStatus.CANCELLED, PROMISE_ID)));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.update(any())).thenReturn(1);

        service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, null, null, null, null, PatchValue.of(CalendarEventStatus.CANCELLED)
        ));

        verify(promiseCommandService, never()).revertScheduled(anyLong(), anyLong());
    }

    /** 잠금 조회와 UPDATE 사이에 행이 사라지면 갱신 건수가 0이 된다. */
    @Test
    void 갱신된_행이_없으면_404로_처리한다() {
        when(repository.findForUpdate(STREAMER_ID, EVENT_ID))
                .thenReturn(Optional.of(existingBroadcast()));
        when(repository.existsLinkedSession(EVENT_ID)).thenReturn(false);
        when(repository.update(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(STREAMER_ID, EVENT_ID, patch(
                null, null, PatchValue.of("제목 변경"), null, null, null, null
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_NOT_FOUND");
    }
}

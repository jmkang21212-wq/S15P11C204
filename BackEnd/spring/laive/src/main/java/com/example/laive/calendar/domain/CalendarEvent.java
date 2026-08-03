package com.example.laive.calendar.domain;

import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.common.api.PatchValue;

import java.time.Instant;

/**
 * 일정의 불변조건을 보관하는 도메인 객체다.
 *
 * 생성·수정·DB 복원 어느 경로로 만들어져도 같은 규칙을 통과하므로, Controller 검증을 거치지
 * 않는 내부 호출에서도 잘못된 일정이 만들어지지 않는다. DB의 CHECK 제약이 같은 규칙을 한 번 더
 * 보장한다.
 *
 * <p>promiseId는 이 일정이 어느 약속에서 전환됐는지를 가리킨다. null이면 사용자가 직접 만든
 * 일정이다. 출처는 만들 때 정해지고 이후 바뀌지 않으므로 PATCH 대상이 아니다.
 */
public record CalendarEvent(
        Long calendarEventId,
        long streamerId,
        Long platformAccountId,
        CalendarEventType eventType,
        String title,
        String memo,
        Instant startAt,
        Instant endAt,
        CalendarEventStatus status,
        Long promiseId
) {
    private static final int TITLE_MAX_LENGTH = 200;

    public CalendarEvent {
        if (eventType == null || status == null) {
            throw CalendarEventException.invalidField();
        }
        title = normalizeTitle(title);
        memo = normalizeMemo(memo);
        requireValidPeriod(startAt, endAt);
        if (eventType.requiresPlatformAccount() && platformAccountId == null) {
            throw CalendarEventException.platformAccountRequired();
        }
    }

    /** 새 일정은 항상 SCHEDULED로 시작한다. */
    public static CalendarEvent schedule(long streamerId, CreateCalendarEventCommand command) {
        return new CalendarEvent(
                null,
                streamerId,
                command.platformAccountId(),
                command.eventType(),
                command.title(),
                command.memo(),
                command.startAt(),
                command.endAt(),
                CalendarEventStatus.SCHEDULED,
                null
        );
    }

    /**
     * 약속에서 전환된 방송 일정을 만든다.
     *
     * <p>BROADCAST는 플랫폼 계정이 필수라(ck_calendar_events_broadcast_platform) 호출 측이
     * 연결된 계정을 찾아 넘긴다. 계정이 없으면 여기 오기 전에 409로 끝난다.
     */
    public static CalendarEvent fromPromise(
            long streamerId,
            long promiseId,
            long platformAccountId,
            String title,
            String memo,
            Instant startAt,
            Instant endAt
    ) {
        return new CalendarEvent(
                null,
                streamerId,
                platformAccountId,
                CalendarEventType.BROADCAST,
                title,
                memo,
                startAt,
                endAt,
                CalendarEventStatus.SCHEDULED,
                promiseId
        );
    }

    /** INSERT가 발급한 ID를 붙인 일정을 만든다. */
    public CalendarEvent withId(long id) {
        return new CalendarEvent(
                id,
                streamerId,
                platformAccountId,
                eventType,
                title,
                memo,
                startAt,
                endAt,
                status,
                promiseId
        );
    }

    /** 이 일정이 약속에서 전환된 것인지. 취소 시 약속을 되돌려야 하는지 판단에 쓴다. */
    public boolean isFromPromise() {
        return promiseId != null;
    }

    /**
     * 이 수정으로 일정이 취소로 넘어가는지.
     *
     * <p>이미 CANCELLED인 일정에 같은 요청을 다시 보내는 것은 전이가 아니다. 그 구분이 없으면
     * 재전송이 약속을 또 되돌리려 시도한다.
     */
    public boolean becomesCancelled(CalendarEvent updated) {
        return status != CalendarEventStatus.CANCELLED
                && updated.status() == CalendarEventStatus.CANCELLED;
    }

    /**
     * 부분 수정 요청을 현재 값에 병합한 새 일정을 만든다.
     *
     * 병합한 뒤 생성자를 다시 통과하므로, 유형만 BROADCAST로 바꾸고 플랫폼 계정을 보내지 않은
     * 요청처럼 "각 필드는 멀쩡하지만 합치면 규칙 위반"인 경우도 걸러진다.
     */
    public CalendarEvent applyPatch(UpdateCalendarEventCommand command, boolean linkedToSession) {
        if (linkedToSession) {
            requireSessionLockedFieldsUnchanged(command);
        }
        return new CalendarEvent(
                calendarEventId,
                streamerId,
                PatchValue.resolve(command.platformAccountId(), platformAccountId),
                PatchValue.resolve(command.eventType(), eventType),
                PatchValue.resolve(command.title(), title),
                PatchValue.resolve(command.memo(), memo),
                PatchValue.resolve(command.startAt(), startAt),
                PatchValue.resolve(command.endAt(), endAt),
                PatchValue.resolve(command.status(), status),
                promiseId
        );
    }

    /**
     * 이미 방송 세션이 참조하는 일정은 시간·플랫폼·유형을 바꿀 수 없다 (API 8.3).
     *
     * 같은 값을 다시 보낸 요청은 변경이 아니므로 통과시킨다. 클라이언트가 전체 폼을 그대로
     * 되돌려 보내도 제목·메모 수정은 계속 되어야 하기 때문이다.
     */
    private void requireSessionLockedFieldsUnchanged(UpdateCalendarEventCommand command) {
        boolean changesLockedField =
                PatchValue.changes(command.startAt(), startAt)
                        || PatchValue.changes(command.endAt(), endAt)
                        || PatchValue.changes(command.platformAccountId(), platformAccountId)
                        || PatchValue.changes(command.eventType(), eventType);
        if (changesLockedField) {
            throw CalendarEventException.linkedToSession();
        }
    }

    private static String normalizeTitle(String value) {
        if (value == null) {
            throw CalendarEventException.invalidTitle();
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > TITLE_MAX_LENGTH) {
            throw CalendarEventException.invalidTitle();
        }
        return normalized;
    }

    private static String normalizeMemo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void requireValidPeriod(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw CalendarEventException.invalidPeriod();
        }
    }
}

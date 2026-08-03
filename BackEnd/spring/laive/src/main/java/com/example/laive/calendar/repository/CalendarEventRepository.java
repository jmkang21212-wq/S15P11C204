package com.example.laive.calendar.repository;

import com.example.laive.calendar.domain.CalendarEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 일정 캘린더 애플리케이션 계층이 사용하는 영속성 Port다.
 */
public interface CalendarEventRepository {

    Optional<PlatformAccountReference> findOwnedPlatformAccount(long streamerId, long platformAccountId);

    List<CalendarEvent> findRange(long streamerId, Instant from, Instant to);

    Optional<CalendarEvent> findDetail(long streamerId, long calendarEventId);

    Optional<CalendarEvent> findForUpdate(long streamerId, long calendarEventId);

    boolean existsLinkedSession(long calendarEventId);

    /** 약속에 연결된 활성(취소되지 않은) 일정의 ID다. 전환 중복 검사에 쓴다. */
    Optional<Long> findActivePromiseEventId(long promiseId);

    /** 약속 전환용 방송 일정에 붙일 연결된 플랫폼 계정이다. 없으면 전환할 수 없다. */
    Optional<Long> findPrimaryConnectedPlatformAccountId(long streamerId);

    long insert(CalendarEvent event);

    int update(CalendarEvent event);
}

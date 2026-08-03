package com.example.laive.calendar.repository;

import com.example.laive.calendar.domain.CalendarEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * 일정 캘린더 애플리케이션 계층이 사용하는 영속성 Port다.
 */
public interface CalendarEventRepository {

    Optional<PlatformAccountReference> findOwnedPlatformAccount(long streamerId, long platformAccountId);

    List<CalendarEvent> findRange(long streamerId, Instant from, Instant to);

    Optional<CalendarEvent> findDetail(long streamerId, long calendarEventId);

    Optional<CalendarEvent> findForUpdate(long streamerId, long calendarEventId);

    boolean existsLinkedSession(long calendarEventId);

    List<CalendarEventPlatformAccountLink> findLinkedPlatformAccounts(
            Collection<Long> calendarEventIds
    );

    void replaceLinkedPlatformAccounts(long calendarEventId, List<Long> platformAccountIds);

    long insert(CalendarEvent event);

    int update(CalendarEvent event);
}

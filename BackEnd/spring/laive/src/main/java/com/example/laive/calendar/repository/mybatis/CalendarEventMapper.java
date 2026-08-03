package com.example.laive.calendar.repository.mybatis;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.repository.PlatformAccountReference;
import com.example.laive.calendar.repository.CalendarEventPlatformAccountLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/calendar/CalendarEventMapper.xml에 둔다.
 */
@Mapper
public interface CalendarEventMapper {

    Optional<PlatformAccountReference> findOwnedPlatformAccount(
            @Param("streamerId") long streamerId,
            @Param("platformAccountId") long platformAccountId
    );

    List<CalendarEvent> findRange(
            @Param("streamerId") long streamerId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    Optional<CalendarEvent> findDetail(
            @Param("streamerId") long streamerId,
            @Param("calendarEventId") long calendarEventId
    );

    Optional<CalendarEvent> findForUpdate(
            @Param("streamerId") long streamerId,
            @Param("calendarEventId") long calendarEventId
    );

    boolean existsLinkedSession(@Param("calendarEventId") long calendarEventId);

    List<CalendarEventPlatformAccountLink> findLinkedPlatformAccounts(
            @Param("calendarEventIds") Collection<Long> calendarEventIds
    );

    int deleteLinkedPlatformAccounts(@Param("calendarEventId") long calendarEventId);

    int insertLinkedPlatformAccounts(
            @Param("calendarEventId") long calendarEventId,
            @Param("platformAccountIds") List<Long> platformAccountIds
    );

    Long insertEvent(@Param("event") CalendarEvent event);

    int updateEvent(@Param("event") CalendarEvent event);
}

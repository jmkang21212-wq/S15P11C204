package com.example.laive.calendar.repository.mybatis;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.calendar.repository.PlatformAccountReference;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper 타입이 Service 계층으로 노출되지 않게 하는 Repository Adapter다.
 */
@Repository
public class MyBatisCalendarEventRepository implements CalendarEventRepository {

    private final CalendarEventMapper mapper;

    public MyBatisCalendarEventRepository(CalendarEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PlatformAccountReference> findOwnedPlatformAccount(
            long streamerId,
            long platformAccountId
    ) {
        return mapper.findOwnedPlatformAccount(streamerId, platformAccountId);
    }

    @Override
    public List<CalendarEvent> findRange(long streamerId, Instant from, Instant to) {
        return mapper.findRange(streamerId, from, to);
    }

    @Override
    public Optional<CalendarEvent> findDetail(long streamerId, long calendarEventId) {
        return mapper.findDetail(streamerId, calendarEventId);
    }

    @Override
    public Optional<CalendarEvent> findForUpdate(long streamerId, long calendarEventId) {
        return mapper.findForUpdate(streamerId, calendarEventId);
    }

    @Override
    public boolean existsLinkedSession(long calendarEventId) {
        return mapper.existsLinkedSession(calendarEventId);
    }

    @Override
    public Optional<Long> findActivePromiseEventId(long promiseId) {
        return mapper.findActivePromiseEventId(promiseId);
    }

    @Override
    public Optional<Long> findPrimaryConnectedPlatformAccountId(long streamerId) {
        return mapper.findPrimaryConnectedPlatformAccountId(streamerId);
    }

    @Override
    public long insert(CalendarEvent event) {
        Long id = mapper.insertEvent(event);
        if (id == null) {
            throw new IllegalStateException("일정 INSERT 결과에 ID가 없습니다.");
        }
        return id;
    }

    @Override
    public int update(CalendarEvent event) {
        return mapper.updateEvent(event);
    }
}

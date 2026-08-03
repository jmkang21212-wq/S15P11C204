package com.example.laive.calendar.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 소유권 조건이 포함된 일정 조회를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class CalendarEventQueryService {

    /** API 8.1이 정한 조회 범위 상한이다. 월간 캘린더의 앞뒤 여백까지 한 번에 담을 수 있다. */
    private static final Duration MAX_RANGE = Duration.ofDays(62);

    private final CalendarEventRepository repository;

    public CalendarEventQueryService(CalendarEventRepository repository) {
        this.repository = repository;
    }

    public List<CalendarEvent> getEvents(long streamerId, Instant from, Instant to) {
        requireValidRange(from, to);
        return repository.findRange(streamerId, from, to);
    }

    /** ID와 로그인 streamerId를 함께 조건으로 사용해 타인의 일정 존재를 숨긴다. */
    public CalendarEvent getEvent(long streamerId, long calendarEventId) {
        return repository.findDetail(streamerId, calendarEventId)
                .orElseThrow(CalendarEventException::notFound);
    }

    public Map<Long, List<Long>> getPlatformAccountIds(List<CalendarEvent> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        repository.findLinkedPlatformAccounts(
                        events.stream().map(CalendarEvent::calendarEventId).toList())
                .forEach(link -> result.computeIfAbsent(
                                link.calendarEventId(), ignored -> new java.util.ArrayList<>())
                        .add(link.platformAccountId()));
        for (CalendarEvent event : events) {
            if (!result.containsKey(event.calendarEventId()) && event.platformAccountId() != null) {
                result.put(event.calendarEventId(), List.of(event.platformAccountId()));
            }
        }
        return result;
    }

    public List<Long> getPlatformAccountIds(CalendarEvent event) {
        return getPlatformAccountIds(List.of(event))
                .getOrDefault(event.calendarEventId(), List.of());
    }

    private void requireValidRange(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw CalendarEventException.rangeInvalid();
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw CalendarEventException.rangeTooWide();
        }
    }
}

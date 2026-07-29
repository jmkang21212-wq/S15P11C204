package com.example.laive.calendar.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.calendar.repository.PlatformAccountReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 일정 생성과 수정·취소를 담당한다.
 */
@Service
@Transactional
public class CalendarEventCommandService {

    private final CalendarEventRepository repository;

    public CalendarEventCommandService(CalendarEventRepository repository) {
        this.repository = repository;
    }

    public CalendarEvent create(long streamerId, CreateCalendarEventCommand command) {
        CalendarEvent event = CalendarEvent.schedule(streamerId, command);
        requireUsablePlatformAccount(streamerId, event.platformAccountId());
        return event.withId(repository.insert(event));
    }

    /**
     * 현재 일정을 잠그고 요청을 병합해 저장한다.
     *
     * 세션 연결 여부와 병합 결과 검증이 모두 잠금 안에서 일어나야, 같은 일정에 대한 동시 수정이
     * 서로의 검증을 무의미하게 만들지 않는다.
     */
    public CalendarEvent update(long streamerId, long calendarEventId, UpdateCalendarEventCommand command) {
        CalendarEvent current = repository.findForUpdate(streamerId, calendarEventId)
                .orElseThrow(CalendarEventException::notFound);

        CalendarEvent updated = current.applyPatch(command, repository.existsLinkedSession(calendarEventId));

        if (!Objects.equals(updated.platformAccountId(), current.platformAccountId())) {
            requireUsablePlatformAccount(streamerId, updated.platformAccountId());
        }

        if (repository.update(updated) != 1) {
            throw CalendarEventException.notFound();
        }
        return updated;
    }

    /** DAY_OFF가 플랫폼 계정을 참조할 수도 있으므로 유형이 아니라 값 유무로 검증한다. */
    private void requireUsablePlatformAccount(long streamerId, Long platformAccountId) {
        if (platformAccountId == null) {
            return;
        }
        PlatformAccountReference account = repository
                .findOwnedPlatformAccount(streamerId, platformAccountId)
                .orElseThrow(CalendarEventException::platformAccountNotFound);
        if (!account.isConnected()) {
            throw CalendarEventException.platformAccountNotConnected();
        }
    }
}

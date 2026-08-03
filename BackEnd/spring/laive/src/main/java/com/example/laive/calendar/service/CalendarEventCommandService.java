package com.example.laive.calendar.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.calendar.repository.PlatformAccountReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        List<Long> platformAccountIds = normalize(command.platformAccountIds());
        requireUsablePlatformAccounts(streamerId, platformAccountIds);
        CalendarEvent created = event.withId(repository.insert(event));
        repository.replaceLinkedPlatformAccounts(created.calendarEventId(), platformAccountIds);
        return created;
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

        List<Long> currentPlatformAccountIds = repository
                .findLinkedPlatformAccounts(List.of(calendarEventId))
                .stream()
                .map(link -> link.platformAccountId())
                .toList();
        if (currentPlatformAccountIds.isEmpty() && current.platformAccountId() != null) {
            currentPlatformAccountIds = List.of(current.platformAccountId());
        }

        boolean platformSelectionPatched = command.platformAccountIds() != null
                || command.platformAccountId() != null;
        List<Long> updatedPlatformAccountIds = resolvePlatformAccountIds(
                command,
                currentPlatformAccountIds
        );
        UpdateCalendarEventCommand effectiveCommand = platformSelectionPatched
                ? new UpdateCalendarEventCommand(
                    com.example.laive.common.api.PatchValue.of(
                            updatedPlatformAccountIds.isEmpty()
                                    ? null
                                    : updatedPlatformAccountIds.getFirst()),
                    command.platformAccountIds(),
                    command.eventType(),
                    command.title(),
                    command.memo(),
                    command.startAt(),
                    command.endAt(),
                    command.status())
                : command;

        boolean linkedToSession = repository.existsLinkedSession(calendarEventId);
        if (linkedToSession && !updatedPlatformAccountIds.equals(currentPlatformAccountIds)) {
            throw CalendarEventException.linkedToSession();
        }

        CalendarEvent updated = current.applyPatch(effectiveCommand, linkedToSession);

        if (!updatedPlatformAccountIds.equals(currentPlatformAccountIds)) {
            requireUsablePlatformAccounts(streamerId, updatedPlatformAccountIds);
        }

        if (repository.update(updated) != 1) {
            throw CalendarEventException.notFound();
        }
        if (!updatedPlatformAccountIds.equals(currentPlatformAccountIds)) {
            repository.replaceLinkedPlatformAccounts(calendarEventId, updatedPlatformAccountIds);
        }
        return updated;
    }

    private List<Long> resolvePlatformAccountIds(
            UpdateCalendarEventCommand command,
            List<Long> current
    ) {
        if (command.platformAccountIds() != null) {
            return normalize(command.platformAccountIds().value());
        }
        if (command.platformAccountId() != null) {
            Long value = command.platformAccountId().value();
            return value == null ? List.of() : List.of(value);
        }
        return current;
    }

    private List<Long> normalize(List<Long> platformAccountIds) {
        if (platformAccountIds == null || platformAccountIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new java.util.LinkedHashSet<>(platformAccountIds));
    }

    private void requireUsablePlatformAccounts(long streamerId, List<Long> platformAccountIds) {
        for (Long platformAccountId : platformAccountIds) {
            requireUsablePlatformAccount(streamerId, platformAccountId);
        }
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

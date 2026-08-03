package com.example.laive.calendar.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.calendar.repository.PlatformAccountReference;
import com.example.laive.promise.service.PromiseCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 일정 생성과 수정·취소를 담당한다.
 *
 * <p>약속에서 전환된 일정을 취소하면 원래 약속을 되돌려야 하므로 promise 쪽 상태 전이를 함께
 * 호출한다. 반대 방향(약속 → 일정 전환)은 {@code PromiseScheduleService}가 담당한다.
 */
@Service
@Transactional
public class CalendarEventCommandService {

    private final CalendarEventRepository repository;
    private final PromiseCommandService promiseCommandService;

    public CalendarEventCommandService(
            CalendarEventRepository repository,
            PromiseCommandService promiseCommandService
    ) {
        this.repository = repository;
        this.promiseCommandService = promiseCommandService;
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
     *
     * 취소(status = CANCELLED)로 넘어가는 수정이고 약속에서 전환된 일정이면, 같은 트랜잭션에서
     * 그 약속을 PENDING으로 되돌린다. 그래야 약속이 "기한 미정" 목록에 다시 나타나 사용자가
     * 다른 날짜로 재확정할 수 있다 — 되돌리지 않으면 어느 화면에도 안 보이는 미아가 된다.
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

        if (current.isFromPromise() && current.becomesCancelled(updated)) {
            promiseCommandService.revertScheduled(streamerId, current.promiseId());
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

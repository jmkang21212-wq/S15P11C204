package com.example.laive.promise.service;

import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.SchedulePromiseCommand;
import com.example.laive.promise.exception.PromiseException;
import com.example.laive.promise.repository.PromiseRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정된 약속을 캘린더 일정으로 전환한다.
 *
 * <p>일정 INSERT와 약속 상태 변경이 한 트랜잭션이어야 하는 것이 이 클래스의 존재 이유다.
 * 프론트에서 "일정 생성" → "약속 상태 변경"을 두 번 호출하면 1번이 성공하고 2번이 실패했을 때
 * 되돌릴 방법이 없다. 일정은 생겼는데 약속은 그대로인 중간 상태가 남는다.
 *
 * <p>계층 방향에 대해: 이 서비스는 calendar 패키지의 Repository를 직접 쓴다. 전환의 반대편인
 * "일정 취소 → 약속 복귀"는 {@code CalendarEventCommandService}가 promise 쪽을 호출하므로
 * 두 패키지는 이미 서로를 안다. 빈 의존은 한 방향으로만 흐른다
 * (PromiseScheduleService → CalendarEventRepository, CalendarEventCommandService →
 * PromiseCommandService) 이라 순환 주입은 생기지 않는다.
 */
@Service
@Transactional
public class PromiseScheduleService {

    /** calendar_events.title이 VARCHAR(200)이다. 약속 content는 500자까지 올 수 있다. */
    private static final int TITLE_MAX_LENGTH = 200;

    private final PromiseRepository promiseRepository;
    private final CalendarEventRepository calendarEventRepository;

    public PromiseScheduleService(
            PromiseRepository promiseRepository,
            CalendarEventRepository calendarEventRepository
    ) {
        this.promiseRepository = promiseRepository;
        this.calendarEventRepository = calendarEventRepository;
    }

    /**
     * 약속을 방송 일정으로 확정한다.
     *
     * <p>검증은 모두 약속 행을 잠근 뒤에 한다. 그래야 같은 약속에 대한 동시 요청이 서로의
     * 검증을 무의미하게 만들지 않는다. 잠금이 놓치는 마지막 경우(두 요청이 서로 다른 약속으로
     * 들어와 같은 일정을 노리는 일은 없으나, 잠금 획득 순서에 따라 중복 INSERT가 될 수 있는
     * 경우)는 ux_calendar_events_promise가 막고 여기서 409로 바꾼다.
     */
    public CalendarEvent schedule(long streamerId, long promiseId, SchedulePromiseCommand command) {
        Promise promise = promiseRepository.findForUpdate(streamerId, promiseId)
                .orElseThrow(PromiseException::notFound);

        // 상태 규칙은 도메인이 갖고 있다. PENDING·ON_HOLD가 아니면 여기서 409로 끝난다.
        Promise scheduled = promise.schedule();

        // 잠금과 같은 조회에서 읽은 값이라 추가 질의 없이 일관되게 판단할 수 있다.
        if (promise.calendarEventId() != null) {
            throw PromiseException.alreadyScheduled();
        }

        long platformAccountId = calendarEventRepository
                .findPrimaryConnectedPlatformAccountId(streamerId)
                .orElseThrow(PromiseException::platformAccountRequired);

        // 기간 검증(endAt > startAt)은 CalendarEvent 생성자가 400으로 처리한다.
        CalendarEvent event = CalendarEvent.fromPromise(
                streamerId,
                promiseId,
                platformAccountId,
                resolveTitle(command.title(), promise.content()),
                command.memo(),
                command.startAt(),
                command.endAt()
        );

        CalendarEvent saved = event.withId(insert(event));

        if (promiseRepository.update(scheduled) != 1) {
            throw PromiseException.notFound();
        }
        return saved;
    }

    private long insert(CalendarEvent event) {
        try {
            return calendarEventRepository.insert(event);
        } catch (DuplicateKeyException e) {
            // ux_calendar_events_promise 위반. 다른 요청이 먼저 같은 약속을 전환했다.
            throw PromiseException.alreadyScheduled();
        }
    }

    /**
     * 제목을 생략하면 약속 문구를 제목 길이로 잘라 쓴다.
     *
     * <p>사용자가 보낸 title은 자르지 않는다 — 200자 초과는 요청 검증에서 400이다. 여기서
     * 자르는 것은 서버가 만들어 낸 기본값뿐이라 "보낸 것과 저장된 것이 다르다"가 생기지 않는다.
     */
    private static String resolveTitle(String requested, String content) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return content.length() <= TITLE_MAX_LENGTH
                ? content
                : content.substring(0, TITLE_MAX_LENGTH);
    }
}

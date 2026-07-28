package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.domain.BroadcastSession;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.EndBroadcastSessionResult;
import com.example.laive.broadcast.session.domain.SessionStatistics;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;
import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import com.example.laive.broadcast.session.event.OutboxEventFactory;
import com.example.laive.broadcast.session.event.OutboxEventFactory.PendingOutboxEvent;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.broadcast.session.repository.BroadcastSessionRepository;
import com.example.laive.broadcast.session.repository.CalendarEventReference;
import com.example.laive.broadcast.session.repository.PlatformAccountReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 방송 세션과 Outbox 변경을 원자적으로 커밋하는 트랜잭션 전용 Service다.
 */
@Service
public class BroadcastSessionTransactionService {

    private static final String CHZZK = "CHZZK";
    private static final String CONNECTED = "CONNECTED";
    private static final String REAUTH_REQUIRED = "REAUTH_REQUIRED";

    private final BroadcastSessionRepository sessionRepository;
    private final BroadcastOutboxRepository outboxRepository;
    private final OutboxEventFactory eventFactory;
    private final Clock clock;

    public BroadcastSessionTransactionService(
            BroadcastSessionRepository sessionRepository,
            BroadcastOutboxRepository outboxRepository,
            OutboxEventFactory eventFactory,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.outboxRepository = outboxRepository;
        this.eventFactory = eventFactory;
        this.clock = clock;
    }

    /** 플랫폼·일정 검증, PREPARING INSERT와 준비 이벤트 저장을 한 번에 커밋한다. */
    @Transactional
    public StartBroadcastSessionResult start(
            long streamerId,
            StartBroadcastSessionCommand command
    ) {
        PlatformAccountReference platformAccount = requirePlatformAccount(
                streamerId,
                command.platformAccountId()
        );
        if (command.calendarEventId() != null) {
            requireCalendarEvent(
                    streamerId,
                    command.calendarEventId(),
                    platformAccount.platformAccountId()
            );
        }
        if (sessionRepository.existsActive(streamerId)) {
            throw BroadcastSessionException.alreadyLive();
        }

        Instant now = clock.instant();
        BroadcastSession session = BroadcastSession.prepare(
                streamerId,
                command.platformAccountId(),
                command.calendarEventId(),
                command.title(),
                command.category(),
                command.language(),
                now
        );

        long sessionId;
        try {
            sessionId = sessionRepository.insert(session);
        } catch (DuplicateKeyException exception) {
            // 방송 세션 INSERT에서 발생 가능한 업무상 Unique 충돌은 활성 세션 중복이다.
            throw BroadcastSessionException.alreadyLive();
        }

        save(eventFactory.preparationRequested(
                sessionId,
                streamerId,
                command.platformAccountId(),
                now,
                session.language(),
                now
        ));
        return new StartBroadcastSessionResult(sessionId, BroadcastStatus.PREPARING, now);
    }

    /** 행 잠금 후 정상 종료, 일정 완료, 리포트 생성과 종료 이벤트를 한 번에 커밋한다. */
    @Transactional
    public EndBroadcastSessionResult end(long streamerId, long sessionId) {
        BroadcastSession session = sessionRepository
                .findByIdForUpdate(streamerId, sessionId)
                .orElseThrow(BroadcastSessionException::notFound);

        Instant endedAt = clock.instant();
        session.end(endedAt);
        if (sessionRepository.markEnded(sessionId, endedAt) != 1) {
            throw BroadcastSessionException.stateConflict();
        }

        if (session.calendarEventId() != null) {
            sessionRepository.completeCalendarEvent(streamerId, session.calendarEventId());
        }

        long reportId = sessionRepository.createProcessingReport(sessionId);
        save(eventFactory.ended(
                sessionId,
                streamerId,
                session.platformAccountId(),
                reportId,
                session.startedAt(),
                endedAt,
                endedAt
        ));
        return new EndBroadcastSessionResult(
                sessionId,
                BroadcastStatus.ENDED,
                "PROCESSING"
        );
    }

    /** 준비 이벤트가 재전달되어도 LIVE 전환과 Started 이벤트는 한 번만 생성한다. */
    @Transactional
    public void activate(long sessionId) {
        BroadcastSession session = sessionRepository.findById(sessionId)
                .orElseThrow(BroadcastSessionException::notFound);
        if (session.status() == BroadcastStatus.LIVE || session.isTerminal()) {
            return;
        }

        Instant now = clock.instant();
        session.activate(now);
        if (sessionRepository.markLive(sessionId) != 1) {
            return;
        }
        save(eventFactory.started(
                sessionId,
                session.streamerId(),
                session.startedAt(),
                now
        ));
    }

    /** 초기화 재시도가 모두 실패한 활성 세션을 최종 FAILED로 바꾼다. */
    @Transactional
    public void failInitialization(long sessionId) {
        BroadcastSession session = sessionRepository.findById(sessionId)
                .orElseThrow(BroadcastSessionException::notFound);
        if (session.isTerminal()) {
            return;
        }
        Instant failedAt = clock.instant();
        session.fail(failedAt);
        sessionRepository.markFailed(sessionId, failedAt);
    }

    /** 원천 테이블 통계를 계산해 PROCESSING 리포트에 기록한다. */
    @Transactional
    public SessionStatistics finalizeStatistics(long reportId, long sessionId) {
        SessionStatistics statistics = sessionRepository.calculateStatistics(sessionId);
        if (sessionRepository.updateReportStatistics(reportId, statistics) != 1) {
            throw new IllegalStateException("PROCESSING 리포트에 종료 통계를 기록할 수 없습니다.");
        }
        return statistics;
    }

    @Transactional
    public void markReportPartial(long reportId) {
        sessionRepository.markReportPartial(reportId, clock.instant());
    }

    @Transactional
    public void markReportFailed(long reportId) {
        sessionRepository.markReportFailed(reportId);
    }

    private PlatformAccountReference requirePlatformAccount(
            long streamerId,
            long platformAccountId
    ) {
        PlatformAccountReference account = sessionRepository
                .findOwnedPlatformAccount(streamerId, platformAccountId)
                .orElseThrow(BroadcastSessionException::platformAccountNotFound);
        if (REAUTH_REQUIRED.equals(account.connectionStatus())) {
            throw BroadcastSessionException.platformReauthRequired();
        }
        if (!CHZZK.equals(account.platform()) || !CONNECTED.equals(account.connectionStatus())) {
            throw BroadcastSessionException.platformConnectionFailed();
        }
        return account;
    }

    private void requireCalendarEvent(
            long streamerId,
            long calendarEventId,
            long platformAccountId
    ) {
        CalendarEventReference calendarEvent = sessionRepository
                .findOwnedCalendarEvent(streamerId, calendarEventId)
                .orElseThrow(BroadcastSessionException::calendarEventNotFound);
        boolean usable = "BROADCAST".equals(calendarEvent.eventType())
                && "SCHEDULED".equals(calendarEvent.status())
                && calendarEvent.platformAccountId() != null
                && calendarEvent.platformAccountId() == platformAccountId;
        if (!usable) {
            throw BroadcastSessionException.calendarEventStateConflict();
        }
    }

    private void save(PendingOutboxEvent event) {
        outboxRepository.save(
                event.eventId(),
                event.aggregateId(),
                event.eventType(),
                event.deduplicationKey(),
                event.payload(),
                event.availableAt()
        );
    }
}


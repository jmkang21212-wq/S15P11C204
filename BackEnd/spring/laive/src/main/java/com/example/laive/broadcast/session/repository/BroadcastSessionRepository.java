package com.example.laive.broadcast.session.repository;

import com.example.laive.broadcast.session.domain.BroadcastSession;
import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.domain.BroadcastSessionListItem;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.SessionStatistics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 방송 세션 애플리케이션 계층이 사용하는 영속성 Port다.
 */
public interface BroadcastSessionRepository {

    Optional<PlatformAccountReference> findOwnedPlatformAccount(long streamerId, long platformAccountId);

    Optional<CalendarEventReference> findOwnedCalendarEvent(long streamerId, long calendarEventId);

    boolean existsActive(long streamerId);

    long insert(BroadcastSession session);

    Optional<BroadcastSession> findByIdForUpdate(long streamerId, long sessionId);

    Optional<BroadcastSession> findById(long sessionId);

    int markLive(long sessionId);

    int markEnded(long sessionId, Instant endedAt);

    int markFailed(long sessionId, Instant failedAt);

    int completeCalendarEvent(long streamerId, long calendarEventId);

    long createProcessingReport(long sessionId);

    Optional<BroadcastSessionDetail> findCurrent(long streamerId);

    Optional<BroadcastSessionDetail> findDetail(long streamerId, long sessionId);

    List<BroadcastSessionListItem> findPage(
            long streamerId,
            BroadcastStatus status,
            int limit,
            long offset
    );

    long count(long streamerId, BroadcastStatus status);

    SessionStatistics calculateStatistics(long sessionId);

    int updateReportStatistics(long reportId, SessionStatistics statistics);

    int markReportPartial(long reportId, Instant generatedAt);

    int markReportFailed(long reportId);
}


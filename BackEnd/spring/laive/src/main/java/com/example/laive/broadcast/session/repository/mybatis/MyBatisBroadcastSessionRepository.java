package com.example.laive.broadcast.session.repository.mybatis;

import com.example.laive.broadcast.session.domain.BroadcastSession;
import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.domain.BroadcastSessionListItem;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.SessionStatistics;
import com.example.laive.broadcast.session.repository.BroadcastSessionRepository;
import com.example.laive.broadcast.session.repository.CalendarEventReference;
import com.example.laive.broadcast.session.repository.PlatformAccountReference;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis Mapper 타입이 Service 계층으로 노출되지 않게 하는 Repository Adapter다.
 */
@Repository
public class MyBatisBroadcastSessionRepository implements BroadcastSessionRepository {

    private final BroadcastSessionMapper mapper;

    public MyBatisBroadcastSessionRepository(BroadcastSessionMapper mapper) {
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
    public Optional<CalendarEventReference> findOwnedCalendarEvent(
            long streamerId,
            long calendarEventId
    ) {
        return mapper.findOwnedCalendarEvent(streamerId, calendarEventId);
    }

    @Override
    public boolean existsActive(long streamerId) {
        return mapper.existsActive(streamerId);
    }

    @Override
    public long insert(BroadcastSession session) {
        Long id = mapper.insertSession(session);
        if (id == null) {
            throw new IllegalStateException("방송 세션 INSERT 결과에 ID가 없습니다.");
        }
        return id;
    }

    @Override
    public Optional<BroadcastSession> findByIdForUpdate(long streamerId, long sessionId) {
        return mapper.findByIdForUpdate(streamerId, sessionId).map(BroadcastSessionRow::toDomain);
    }

    @Override
    public Optional<BroadcastSession> findById(long sessionId) {
        return mapper.findById(sessionId).map(BroadcastSessionRow::toDomain);
    }

    @Override
    public int markLive(long sessionId) {
        return mapper.markLive(sessionId);
    }

    @Override
    public int markEnded(long sessionId, Instant endedAt) {
        return mapper.markEnded(sessionId, endedAt);
    }

    @Override
    public int markFailed(long sessionId, Instant failedAt) {
        return mapper.markFailed(sessionId, failedAt);
    }

    @Override
    public int completeCalendarEvent(long streamerId, long calendarEventId) {
        return mapper.completeCalendarEvent(streamerId, calendarEventId);
    }

    @Override
    public long createProcessingReport(long sessionId) {
        Long id = mapper.createProcessingReport(sessionId);
        if (id == null) {
            throw new IllegalStateException("리포트 INSERT 결과에 ID가 없습니다.");
        }
        return id;
    }

    @Override
    public Optional<BroadcastSessionDetail> findCurrent(long streamerId) {
        return mapper.findCurrent(streamerId);
    }

    @Override
    public Optional<BroadcastSessionDetail> findDetail(long streamerId, long sessionId) {
        return mapper.findDetail(streamerId, sessionId);
    }

    @Override
    public List<BroadcastSessionListItem> findPage(
            long streamerId,
            BroadcastStatus status,
            int limit,
            long offset
    ) {
        return mapper.findPage(streamerId, status, limit, offset);
    }

    @Override
    public long count(long streamerId, BroadcastStatus status) {
        return mapper.count(streamerId, status);
    }

    @Override
    public SessionStatistics calculateStatistics(long sessionId) {
        return mapper.calculateStatistics(sessionId);
    }

    @Override
    public int updateReportStatistics(long reportId, SessionStatistics statistics) {
        return mapper.updateReportStatistics(reportId, statistics);
    }

    @Override
    public int replaceReportPlatformMetrics(long reportId, long sessionId) {
        return mapper.replaceReportPlatformMetrics(reportId, sessionId);
    }

    @Override
    public int markReportPartial(long reportId, Instant generatedAt) {
        return mapper.markReportPartial(reportId, generatedAt);
    }

    @Override
    public int markReportFailed(long reportId) {
        return mapper.markReportFailed(reportId);
    }
}

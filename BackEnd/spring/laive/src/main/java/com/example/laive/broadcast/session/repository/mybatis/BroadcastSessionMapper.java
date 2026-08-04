package com.example.laive.broadcast.session.repository.mybatis;

import com.example.laive.broadcast.session.domain.BroadcastSession;
import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.domain.BroadcastSessionListItem;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.domain.SessionStatistics;
import com.example.laive.broadcast.session.repository.CalendarEventReference;
import com.example.laive.broadcast.session.repository.PlatformAccountReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/BroadcastSessionMapper.xml에 둔다.
 */
@Mapper
public interface BroadcastSessionMapper {

    Optional<PlatformAccountReference> findOwnedPlatformAccount(
            @Param("streamerId") long streamerId,
            @Param("platformAccountId") long platformAccountId
    );

    Optional<CalendarEventReference> findOwnedCalendarEvent(
            @Param("streamerId") long streamerId,
            @Param("calendarEventId") long calendarEventId
    );

    boolean existsActive(@Param("streamerId") long streamerId);

    Long insertSession(@Param("session") BroadcastSession session);

    Optional<BroadcastSessionRow> findByIdForUpdate(
            @Param("streamerId") long streamerId,
            @Param("sessionId") long sessionId
    );

    Optional<BroadcastSessionRow> findById(@Param("sessionId") long sessionId);

    int markLive(@Param("sessionId") long sessionId);

    int markEnded(
            @Param("sessionId") long sessionId,
            @Param("endedAt") Instant endedAt
    );

    int markFailed(
            @Param("sessionId") long sessionId,
            @Param("failedAt") Instant failedAt
    );

    int completeCalendarEvent(
            @Param("streamerId") long streamerId,
            @Param("calendarEventId") long calendarEventId
    );

    Long createProcessingReport(@Param("sessionId") long sessionId);

    Optional<BroadcastSessionDetail> findCurrent(@Param("streamerId") long streamerId);

    Optional<BroadcastSessionDetail> findDetail(
            @Param("streamerId") long streamerId,
            @Param("sessionId") long sessionId
    );

    List<BroadcastSessionListItem> findPage(
            @Param("streamerId") long streamerId,
            @Param("status") BroadcastStatus status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    long count(
            @Param("streamerId") long streamerId,
            @Param("status") BroadcastStatus status
    );

    SessionStatistics calculateStatistics(@Param("sessionId") long sessionId);

    int updateReportStatistics(
            @Param("reportId") long reportId,
            @Param("statistics") SessionStatistics statistics
    );

    int replaceReportPlatformMetrics(
            @Param("reportId") long reportId,
            @Param("sessionId") long sessionId
    );

    int markReportPartial(
            @Param("reportId") long reportId,
            @Param("generatedAt") Instant generatedAt
    );

    int markReportFailed(@Param("reportId") long reportId);
}

package com.example.laive.report.repository.mybatis;

import com.example.laive.broadcast.fandom.domain.TopViewer;
import com.example.laive.report.domain.ReportInsight;
import com.example.laive.report.domain.SessionHighlight;
import com.example.laive.report.domain.ViewerTimelinePoint;
import com.example.laive.report.domain.ReportPlatformMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SessionReportMapper {
    boolean existsOwnedSession(@Param("streamerId") long streamerId, @Param("sessionId") long sessionId);
    Optional<SessionReportRow> findReport(@Param("streamerId") long streamerId, @Param("sessionId") long sessionId);
    List<ViewerTimelinePoint> findViewerTimeline(@Param("sessionId") long sessionId);
    List<ReportTriggerRow> findTriggers(@Param("sessionId") long sessionId);
    List<ReportInsight> findInsights(@Param("reportId") long reportId);
    List<SessionHighlight> findHighlights(@Param("reportId") long reportId);
    List<ReportPlatformMetrics> findPlatformMetrics(@Param("reportId") long reportId);

    /** 이 방송의 열성 시청자 상위 목록. reportId가 아니라 sessionId 기준이다 — 점수는 세션에 달려 있다. */
    List<TopViewer> findTopViewers(@Param("sessionId") long sessionId, @Param("limit") int limit);
}

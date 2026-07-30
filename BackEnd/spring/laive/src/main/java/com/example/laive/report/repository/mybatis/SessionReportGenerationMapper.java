package com.example.laive.report.repository.mybatis;

import com.example.laive.report.ai.InsightResult;
import com.example.laive.report.domain.ChatExcerpt;
import com.example.laive.report.domain.HighlightCandidate;
import com.example.laive.report.domain.RiskExcerpt;
import com.example.laive.report.domain.TranscriptExcerpt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SessionReportGenerationMapper {
    Optional<ReportGenerationContextRow> findGenerationContext(
            @Param("reportId") long reportId, @Param("sessionId") long sessionId
    );
    Optional<ReportTriggerRow> findTrigger(@Param("triggerId") long triggerId);
    List<TranscriptExcerpt> findTranscripts(
            @Param("sessionId") long sessionId, @Param("startMs") long startMs, @Param("endMs") long endMs
    );
    List<ChatExcerpt> findChats(
            @Param("sessionId") long sessionId, @Param("startedAt") Instant startedAt, @Param("endedAt") Instant endedAt
    );
    List<RiskExcerpt> findRisks(
            @Param("sessionId") long sessionId, @Param("startedAt") Instant startedAt, @Param("endedAt") Instant endedAt
    );
    List<Long> markTriggersPending(@Param("sessionId") long sessionId);
    int completeTrigger(@Param("triggerId") long triggerId, @Param("narrative") String narrative,
                        @Param("analyzedAt") Instant analyzedAt);
    int failTrigger(@Param("triggerId") long triggerId, @Param("error") String error,
                    @Param("analyzedAt") Instant analyzedAt);
    boolean allTriggersFinished(@Param("sessionId") long sessionId);
    List<Long> findRiskTriggerIds(@Param("sessionId") long sessionId);
    int deleteInsights(@Param("reportId") long reportId);
    int insertInsight(@Param("reportId") long reportId, @Param("insight") InsightResult insight);
    int upsertHighlight(@Param("reportId") long reportId, @Param("highlight") HighlightCandidate highlight);
    int completeReport(@Param("reportId") long reportId, @Param("summary") String summary,
                       @Param("generatedAt") Instant generatedAt);
    int partialReport(@Param("reportId") long reportId, @Param("summary") String summary,
                      @Param("generatedAt") Instant generatedAt);
    int failReport(@Param("reportId") long reportId);
}

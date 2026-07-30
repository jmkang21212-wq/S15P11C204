package com.example.laive.report.repository;

import com.example.laive.report.ai.InsightResult;
import com.example.laive.report.domain.ChatExcerpt;
import com.example.laive.report.domain.HighlightCandidate;
import com.example.laive.report.domain.ReportGenerationContext;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.RiskExcerpt;
import com.example.laive.report.domain.TranscriptExcerpt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SessionReportGenerationRepository {
    Optional<ReportGenerationContext> findGenerationContext(long reportId, long sessionId);
    Optional<ReportTrigger> findTrigger(long triggerId);
    List<TranscriptExcerpt> findTranscripts(long sessionId, long startMs, long endMs);
    List<ChatExcerpt> findChats(long sessionId, Instant startedAt, Instant endedAt);
    List<RiskExcerpt> findRisks(long sessionId, Instant startedAt, Instant endedAt);
    List<Long> markTriggersPending(long sessionId);
    int completeTrigger(long triggerId, String narrative, Instant analyzedAt);
    int failTrigger(long triggerId, String error, Instant analyzedAt);
    boolean allTriggersFinished(long sessionId);
    Set<Long> findRiskTriggerIds(long sessionId);
    void replaceInsights(long reportId, List<InsightResult> insights);
    void upsertHighlights(long reportId, List<HighlightCandidate> highlights);
    int completeReport(long reportId, String summary, Instant generatedAt);
    int partialReport(long reportId, String summary, Instant generatedAt);
    int failReport(long reportId);
}

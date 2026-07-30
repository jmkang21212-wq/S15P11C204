package com.example.laive.report.repository.mybatis;

import com.example.laive.report.ai.InsightResult;
import com.example.laive.report.domain.ChatExcerpt;
import com.example.laive.report.domain.HighlightCandidate;
import com.example.laive.report.domain.ReportGenerationContext;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.RiskExcerpt;
import com.example.laive.report.domain.TranscriptExcerpt;
import com.example.laive.report.repository.SessionReportGenerationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MyBatisSessionReportGenerationRepository implements SessionReportGenerationRepository {
    private final SessionReportGenerationMapper mapper;

    public MyBatisSessionReportGenerationRepository(SessionReportGenerationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ReportGenerationContext> findGenerationContext(long reportId, long sessionId) {
        return mapper.findGenerationContext(reportId, sessionId).map(ReportGenerationContextRow::toDomain);
    }

    @Override
    public Optional<ReportTrigger> findTrigger(long triggerId) {
        return mapper.findTrigger(triggerId).map(ReportTriggerRow::toDomain);
    }

    @Override
    public List<TranscriptExcerpt> findTranscripts(long sessionId, long startMs, long endMs) {
        return mapper.findTranscripts(sessionId, startMs, endMs);
    }

    @Override
    public List<ChatExcerpt> findChats(long sessionId, Instant startedAt, Instant endedAt) {
        return mapper.findChats(sessionId, startedAt, endedAt);
    }

    @Override
    public List<RiskExcerpt> findRisks(long sessionId, Instant startedAt, Instant endedAt) {
        return mapper.findRisks(sessionId, startedAt, endedAt);
    }

    @Override
    public List<Long> markTriggersPending(long sessionId) {
        return mapper.markTriggersPending(sessionId);
    }

    @Override
    public int completeTrigger(long triggerId, String narrative, Instant analyzedAt) {
        return mapper.completeTrigger(triggerId, narrative, analyzedAt);
    }

    @Override
    public int failTrigger(long triggerId, String error, Instant analyzedAt) {
        return mapper.failTrigger(triggerId, error, analyzedAt);
    }

    @Override
    public boolean allTriggersFinished(long sessionId) {
        return mapper.allTriggersFinished(sessionId);
    }

    @Override
    public Set<Long> findRiskTriggerIds(long sessionId) {
        return new HashSet<>(mapper.findRiskTriggerIds(sessionId));
    }

    @Override
    public void replaceInsights(long reportId, List<InsightResult> insights) {
        mapper.deleteInsights(reportId);
        insights.forEach(insight -> mapper.insertInsight(reportId, insight));
    }

    @Override
    public void upsertHighlights(long reportId, List<HighlightCandidate> highlights) {
        highlights.forEach(highlight -> mapper.upsertHighlight(reportId, highlight));
    }

    @Override
    public int completeReport(long reportId, String summary, Instant generatedAt) {
        return mapper.completeReport(reportId, summary, generatedAt);
    }

    @Override
    public int partialReport(long reportId, String summary, Instant generatedAt) {
        return mapper.partialReport(reportId, summary, generatedAt);
    }

    @Override
    public int failReport(long reportId) {
        return mapper.failReport(reportId);
    }
}

package com.example.laive.report.repository.mybatis;

import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.SentimentCounts;
import com.example.laive.report.domain.TriggerAnalysisStatus;
import com.example.laive.report.domain.TriggerType;

import java.math.BigDecimal;

public record ReportTriggerRow(
        long triggerId,
        long sessionId,
        TriggerType triggerType,
        Long transcriptId,
        long startMs,
        long endMs,
        BigDecimal peakZ,
        BigDecimal peakEngagement,
        int avgViewers,
        int chatCount,
        int uniqueChatters,
        long analyzedChat,
        long joyCount,
        long excitementCount,
        long supportCount,
        long surpriseCount,
        long sadnessCount,
        long angerCount,
        long confusionCount,
        long neutralCount,
        String narrative,
        String screenContext,
        TriggerAnalysisStatus analysisStatus
) {
    public ReportTrigger toDomain() {
        return new ReportTrigger(
                triggerId, sessionId, triggerType, transcriptId, startMs, endMs,
                peakZ, peakEngagement, avgViewers, chatCount, uniqueChatters,
                new SentimentCounts(
                        analyzedChat, joyCount, excitementCount, supportCount, surpriseCount,
                        sadnessCount, angerCount, confusionCount, neutralCount
                ),
                narrative, screenContext, analysisStatus
        );
    }
}

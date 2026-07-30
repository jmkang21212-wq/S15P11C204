package com.example.laive.report.domain;

import java.math.BigDecimal;

public record ReportTrigger(
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
        SentimentCounts sentimentCounts,
        String narrative,
        String screenContext,
        TriggerAnalysisStatus analysisStatus
) {
}

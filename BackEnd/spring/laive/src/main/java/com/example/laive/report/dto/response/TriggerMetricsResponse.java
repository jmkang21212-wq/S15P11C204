package com.example.laive.report.dto.response;

import com.example.laive.report.domain.ReportTrigger;

import java.math.BigDecimal;

public record TriggerMetricsResponse(
        BigDecimal peakZ,
        BigDecimal peakEngagement,
        int avgViewers,
        int chatCount,
        int uniqueChatters,
        long analyzedChat
) {
    public static TriggerMetricsResponse from(ReportTrigger trigger) {
        return new TriggerMetricsResponse(
                trigger.peakZ(), trigger.peakEngagement(), trigger.avgViewers(),
                trigger.chatCount(), trigger.uniqueChatters(),
                trigger.sentimentCounts().analyzedChat()
        );
    }
}

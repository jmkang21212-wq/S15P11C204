package com.example.laive.report.dto.response;

import com.example.laive.broadcast.realtime.domain.SentimentLabel;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.SentimentDistribution;
import com.example.laive.report.domain.TriggerType;

import java.math.BigDecimal;
import java.util.Map;

public record ReportTriggerResponse(
        long triggerId,
        TriggerType triggerType,
        Long transcriptId,
        long startMs,
        long endMs,
        TriggerMetricsResponse metrics,
        SentimentLabel dominant,
        Map<SentimentLabel, BigDecimal> distribution,
        String narrative,
        String screenContext
) {
    public static ReportTriggerResponse from(ReportTrigger trigger) {
        SentimentDistribution sentiment = SentimentDistribution.from(trigger.sentimentCounts());
        return new ReportTriggerResponse(
                trigger.triggerId(), trigger.triggerType(), trigger.transcriptId(),
                trigger.startMs(), trigger.endMs(), TriggerMetricsResponse.from(trigger),
                sentiment.dominant(), sentiment.distribution(),
                trigger.narrative(), trigger.screenContext()
        );
    }
}

package com.example.laive.report.ai;

import com.example.laive.report.domain.ChatExcerpt;
import com.example.laive.report.domain.ReportGenerationContext;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.RiskExcerpt;
import com.example.laive.report.domain.TranscriptExcerpt;

import java.util.List;

public record TriggerAnalysisInput(
        ReportGenerationContext report,
        ReportTrigger trigger,
        List<TranscriptExcerpt> transcripts,
        List<ChatExcerpt> chats,
        List<RiskExcerpt> risks
) {
}

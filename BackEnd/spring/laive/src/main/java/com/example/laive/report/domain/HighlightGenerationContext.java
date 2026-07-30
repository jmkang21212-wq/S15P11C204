package com.example.laive.report.domain;

import java.util.List;
import java.util.Set;

public record HighlightGenerationContext(
        List<ReportTrigger> triggers,
        Set<Long> riskTriggerIds,
        int maximumHighlights
) {
    public HighlightGenerationContext {
        triggers = List.copyOf(triggers);
        riskTriggerIds = Set.copyOf(riskTriggerIds);
    }
}

package com.example.laive.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "laive.report.ai")
public record ReportAiProperties(
        boolean enabled,
        int maxInputChars,
        int maxOutputTokens,
        double minSentimentCoverage,
        int maxHighlights
) {
}

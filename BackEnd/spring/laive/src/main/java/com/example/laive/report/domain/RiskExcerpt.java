package com.example.laive.report.domain;

import java.time.Instant;

public record RiskExcerpt(Instant detectedAt, String level, String category, String advice) {
}

package com.example.laive.report.domain;

import java.math.BigDecimal;

public record HighlightCandidate(
        long triggerId,
        long startMs,
        long endMs,
        BigDecimal score
) {
}

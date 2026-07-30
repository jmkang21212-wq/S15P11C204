package com.example.laive.report.service;

import com.example.laive.report.domain.HighlightGenerationContext;
import com.example.laive.report.domain.ReportTrigger;
import com.example.laive.report.domain.SentimentCounts;
import com.example.laive.report.domain.TriggerAnalysisStatus;
import com.example.laive.report.domain.TriggerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicHighlightGeneratorTest {
    private final DeterministicHighlightGenerator generator = new DeterministicHighlightGenerator();

    @Test
    void excludesRiskAndOverlappingLowerScoreAndSortsByTime() {
        var first = trigger(1, 10_000, 50_000, "8.0", 100);
        var overlap = trigger(2, 20_000, 55_000, "3.0", 20);
        var later = trigger(3, 80_000, 120_000, "7.0", 80);
        var risk = trigger(4, 140_000, 180_000, "10.0", 200);

        var result = generator.generate(new HighlightGenerationContext(
                List.of(later, overlap, risk, first), Set.of(4L), 5
        ));

        assertThat(result).extracting(candidate -> candidate.triggerId())
                .containsExactly(1L, 3L);
    }

    private ReportTrigger trigger(long id, long start, long end, String z, int unique) {
        return new ReportTrigger(
                id, 10, TriggerType.CHAT_SURGE, null, start, end,
                new BigDecimal(z), new BigDecimal("0.4"), 100, 100, unique,
                new SentimentCounts(100, 40, 20, 10, 10, 5, 5, 5, 5),
                null, null, TriggerAnalysisStatus.COMPLETED
        );
    }
}

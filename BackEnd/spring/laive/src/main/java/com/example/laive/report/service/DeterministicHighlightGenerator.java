package com.example.laive.report.service;

import com.example.laive.report.domain.HighlightCandidate;
import com.example.laive.report.domain.HighlightGenerationContext;
import com.example.laive.report.domain.ReportTrigger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class DeterministicHighlightGenerator implements HighlightGenerator {
    private static final BigDecimal Z_WEIGHT = new BigDecimal("0.45");
    private static final BigDecimal ENGAGEMENT_WEIGHT = new BigDecimal("0.25");
    private static final BigDecimal UNIQUE_WEIGHT = new BigDecimal("0.15");
    private static final BigDecimal EMOTION_WEIGHT = new BigDecimal("0.15");

    @Override
    public List<HighlightCandidate> generate(HighlightGenerationContext context) {
        int maximumUnique = context.triggers().stream()
                .mapToInt(ReportTrigger::uniqueChatters).max().orElse(1);
        List<HighlightCandidate> ranked = context.triggers().stream()
                .filter(trigger -> eligible(trigger, context))
                .map(trigger -> candidate(trigger, maximumUnique))
                .sorted(Comparator.comparing(HighlightCandidate::score).reversed()
                        .thenComparingLong(HighlightCandidate::startMs))
                .toList();
        List<HighlightCandidate> selected = new ArrayList<>();
        for (HighlightCandidate candidate : ranked) {
            if (selected.stream().noneMatch(existing -> overlapRatio(existing, candidate) >= 0.5)) {
                selected.add(candidate);
                if (selected.size() == context.maximumHighlights()) {
                    break;
                }
            }
        }
        return selected.stream().sorted(Comparator.comparingLong(HighlightCandidate::startMs)).toList();
    }

    private boolean eligible(ReportTrigger trigger, HighlightGenerationContext context) {
        long duration = trigger.endMs() - trigger.startMs();
        return trigger.startMs() >= 0 && duration >= 10_000 && duration <= 300_000
                && trigger.chatCount() > 0 && !context.riskTriggerIds().contains(trigger.triggerId());
    }

    private HighlightCandidate candidate(ReportTrigger trigger, int maximumUnique) {
        BigDecimal normalizedZ = trigger.peakZ().divide(BigDecimal.TEN, 6, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE).max(BigDecimal.ZERO);
        BigDecimal unique = BigDecimal.valueOf(trigger.uniqueChatters())
                .divide(BigDecimal.valueOf(Math.max(maximumUnique, 1)), 6, RoundingMode.HALF_UP);
        BigDecimal neutral = trigger.sentimentCounts().analyzedChat() == 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(trigger.sentimentCounts().neutral())
                .divide(BigDecimal.valueOf(trigger.sentimentCounts().analyzedChat()), 6, RoundingMode.HALF_UP);
        BigDecimal score = normalizedZ.multiply(Z_WEIGHT)
                .add(trigger.peakEngagement().multiply(ENGAGEMENT_WEIGHT))
                .add(unique.multiply(UNIQUE_WEIGHT))
                .add(BigDecimal.ONE.subtract(neutral).multiply(EMOTION_WEIGHT))
                .setScale(6, RoundingMode.HALF_UP);
        return new HighlightCandidate(trigger.triggerId(), trigger.startMs(), trigger.endMs(), score);
    }

    private double overlapRatio(HighlightCandidate left, HighlightCandidate right) {
        long overlap = Math.max(0, Math.min(left.endMs(), right.endMs())
                - Math.max(left.startMs(), right.startMs()));
        long shorter = Math.min(left.endMs() - left.startMs(), right.endMs() - right.startMs());
        return shorter == 0 ? 0 : (double) overlap / shorter;
    }
}

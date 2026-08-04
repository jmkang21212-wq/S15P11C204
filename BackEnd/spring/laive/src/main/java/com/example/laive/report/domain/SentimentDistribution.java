package com.example.laive.report.domain;

import com.example.laive.broadcast.realtime.domain.SentimentLabel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

public record SentimentDistribution(
        SentimentLabel dominant,
        Map<SentimentLabel, BigDecimal> distribution
) {
    private static final int SCALE = 4;

    public SentimentDistribution {
        distribution = Map.copyOf(distribution);
    }

    public static SentimentDistribution from(SentimentCounts counts) {
        long[] values = {
                counts.joy(), counts.excitement(), counts.support(), counts.surprise(),
                counts.sadness(), counts.anger(), counts.confusion(), counts.neutral()
        };
        SentimentLabel[] labels = SentimentLabel.values();
        EnumMap<SentimentLabel, BigDecimal> result = new EnumMap<>(SentimentLabel.class);
        SentimentLabel dominant = SentimentLabel.NEUTRAL;
        long maximum = -1;
        for (int index = 0; index < labels.length; index++) {
            long value = values[index];
            if (value > maximum) {
                maximum = value;
                dominant = labels[index];
            }
            result.put(labels[index], counts.analyzedChat() == 0
                    ? BigDecimal.ZERO.setScale(SCALE)
                    : BigDecimal.valueOf(value).divide(
                            BigDecimal.valueOf(counts.analyzedChat()), SCALE, RoundingMode.HALF_UP
                    ));
        }
        if (counts.analyzedChat() == 0) {
            dominant = SentimentLabel.NEUTRAL;
        }
        return new SentimentDistribution(dominant, result);
    }
}

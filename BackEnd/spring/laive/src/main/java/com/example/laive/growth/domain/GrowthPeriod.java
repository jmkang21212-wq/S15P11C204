package com.example.laive.growth.domain;

import com.example.laive.growth.exception.GrowthException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public record GrowthPeriod(LocalDate start, LocalDate end) {
    public static final long MAX_DAYS = 731;

    public GrowthPeriod {
        if (start == null || end == null || end.isBefore(start)
                || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw GrowthException.invalidPeriod();
        }
    }

    public static GrowthPeriod aligned(GrowthPeriodType type, LocalDate start, LocalDate end) {
        GrowthPeriod period = new GrowthPeriod(start, end);
        if (type == null || !type.isAligned(start, end)) {
            throw GrowthException.invalidPeriod();
        }
        return period;
    }

    public Instant startInclusive() {
        return start.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant endExclusive() {
        return end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Duration overlap(Instant sessionStart, Instant sessionEnd) {
        Instant from = sessionStart.isAfter(startInclusive()) ? sessionStart : startInclusive();
        Instant to = sessionEnd.isBefore(endExclusive()) ? sessionEnd : endExclusive();
        return to.isAfter(from) ? Duration.between(from, to) : Duration.ZERO;
    }
}

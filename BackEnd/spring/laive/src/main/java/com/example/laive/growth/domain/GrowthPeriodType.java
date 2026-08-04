package com.example.laive.growth.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum GrowthPeriodType {
    WEEKLY,
    MONTHLY;

    public boolean isAligned(LocalDate from, LocalDate to) {
        return switch (this) {
            case WEEKLY -> from.getDayOfWeek() == DayOfWeek.MONDAY
                    && to.getDayOfWeek() == DayOfWeek.SUNDAY;
            case MONTHLY -> from.getDayOfMonth() == 1
                    && to.equals(to.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }
}

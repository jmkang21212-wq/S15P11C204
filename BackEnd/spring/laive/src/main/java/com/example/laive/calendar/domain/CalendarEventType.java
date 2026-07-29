package com.example.laive.calendar.domain;

/**
 * 일정 유형이다. DAY_OFF는 플랫폼과 무관한 휴방이라 platformAccountId가 없어도 된다 (API 1.5).
 */
public enum CalendarEventType {
    BROADCAST,
    DAY_OFF;

    public boolean requiresPlatformAccount() {
        return this == BROADCAST;
    }
}

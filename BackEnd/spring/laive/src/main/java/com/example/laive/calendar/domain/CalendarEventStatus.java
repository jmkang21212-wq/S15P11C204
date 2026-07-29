package com.example.laive.calendar.domain;

/**
 * 일정 진행 상태다. 취소는 삭제가 아니라 CANCELLED 전이로 처리한다 (API 8.3).
 */
public enum CalendarEventStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}

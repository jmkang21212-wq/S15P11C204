package com.example.laive.broadcast.session.domain;

/**
 * Controller DTO에서 검증된 방송 시작 입력을 Service로 전달한다.
 */
public record StartBroadcastSessionCommand(
        long platformAccountId,
        Long calendarEventId,
        String title,
        String category,
        String language
) {
}


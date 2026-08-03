package com.example.laive.broadcast.session.domain;

/**
 * Controller DTO에서 검증된 방송 시작 입력을 Service로 전달한다.
 */
public record StartBroadcastSessionCommand(
        java.util.List<Long> platformAccountIds,
        Long calendarEventId,
        String title,
        String category,
        String language
) {
    public StartBroadcastSessionCommand {
        platformAccountIds = java.util.List.copyOf(platformAccountIds);
        if (platformAccountIds.isEmpty()) {
            throw new IllegalArgumentException("at least one platform account is required");
        }
    }

    public StartBroadcastSessionCommand(
            long platformAccountId,
            Long calendarEventId,
            String title,
            String category,
            String language
    ) {
        this(java.util.List.of(platformAccountId), calendarEventId, title, category, language);
    }

    /** Expand 기간 동안 기존 부모 컬럼에 기록할 대표 계정이다. */
    public long platformAccountId() {
        return platformAccountIds.getFirst();
    }
}

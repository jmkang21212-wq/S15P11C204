package com.example.laive.calendar.repository;

/**
 * BROADCAST 일정 검증에 필요한 플랫폼 계정 최소 정보다.
 */
public record PlatformAccountReference(
        long platformAccountId,
        long streamerId,
        String connectionStatus
) {
    public boolean isConnected() {
        return "CONNECTED".equals(connectionStatus);
    }
}

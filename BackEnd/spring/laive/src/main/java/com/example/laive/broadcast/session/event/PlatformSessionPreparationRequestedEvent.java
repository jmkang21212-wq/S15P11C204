package com.example.laive.broadcast.session.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 플랫폼 자식 하나의 live 탐지와 수집기 시작을 요청한다.
 * title은 플랫폼 방송 제목 동기화용이다 — 필드 추가 이전에 저장된 페이로드에서는
 * null로 역직렬화되므로 소비자는 null을 허용해야 한다.
 */
public record PlatformSessionPreparationRequestedEvent(
        UUID eventId,
        short schemaVersion,
        long sessionId,
        long platformSessionId,
        long streamerId,
        long platformAccountId,
        String platformCode,
        Instant requestedAt,
        String language,
        String title,
        Instant occurredAt
) {
}

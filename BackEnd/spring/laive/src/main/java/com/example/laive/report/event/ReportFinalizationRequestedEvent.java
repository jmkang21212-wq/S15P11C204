package com.example.laive.report.event;

import java.time.Instant;
import java.util.UUID;

public record ReportFinalizationRequestedEvent(
        UUID eventId,
        short schemaVersion,
        long sessionId,
        long reportId,
        Instant occurredAt
) {
}

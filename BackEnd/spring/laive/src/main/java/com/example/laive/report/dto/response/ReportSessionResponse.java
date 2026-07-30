package com.example.laive.report.dto.response;

import com.example.laive.report.domain.SessionReport;

import java.time.Instant;

public record ReportSessionResponse(
        long sessionId,
        String title,
        Instant startedAt,
        Instant endedAt
) {
    public static ReportSessionResponse from(SessionReport report) {
        return new ReportSessionResponse(
                report.sessionId(), report.title(), report.startedAt(), report.endedAt()
        );
    }
}

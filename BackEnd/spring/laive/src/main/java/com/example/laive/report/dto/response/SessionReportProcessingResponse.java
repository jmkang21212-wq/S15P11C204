package com.example.laive.report.dto.response;

import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.SessionReportDetail;

public record SessionReportProcessingResponse(
        long reportId,
        ReportStatus status,
        ReportSessionResponse session,
        double progress
) {
    public static SessionReportProcessingResponse from(SessionReportDetail detail) {
        return new SessionReportProcessingResponse(
                detail.report().reportId(), detail.report().status(),
                ReportSessionResponse.from(detail.report()), detail.progress()
        );
    }
}

package com.example.laive.report.exception;

import com.example.laive.common.exception.BusinessException;

public class ReportException extends BusinessException {
    private ReportException(ReportErrorCode code) {
        super(code.status(), code.name(), code.message());
    }

    public static ReportException notFound() {
        return new ReportException(ReportErrorCode.REPORT_NOT_FOUND);
    }

    public static ReportException generating() {
        return new ReportException(ReportErrorCode.REPORT_GENERATING);
    }

    public static ReportException failed() {
        return new ReportException(ReportErrorCode.REPORT_FAILED);
    }
}

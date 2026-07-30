package com.example.laive.report.exception;

public class TriggerAnalysisException extends RuntimeException {
    public TriggerAnalysisException(String message) {
        super(message);
    }

    public TriggerAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}

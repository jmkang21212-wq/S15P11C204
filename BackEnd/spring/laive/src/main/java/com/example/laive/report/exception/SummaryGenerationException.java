package com.example.laive.report.exception;

public class SummaryGenerationException extends RuntimeException {
    public SummaryGenerationException(String message) {
        super(message);
    }

    public SummaryGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

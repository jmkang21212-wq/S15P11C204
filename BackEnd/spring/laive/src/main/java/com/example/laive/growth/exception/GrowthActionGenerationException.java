package com.example.laive.growth.exception;

public class GrowthActionGenerationException extends RuntimeException {
    public GrowthActionGenerationException(String message) {
        super(message);
    }

    public GrowthActionGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

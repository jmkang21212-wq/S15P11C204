package com.example.laive.growth.roadmap.ai;

public class GrowthRoadmapGenerationException extends RuntimeException {
    private final boolean retryable;

    public GrowthRoadmapGenerationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public GrowthRoadmapGenerationException(
            String message, Throwable cause, boolean retryable
    ) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}

package com.example.laive.growth.domain;

public record GrowthAction(
        long id,
        long growthGoalId,
        Long generationId,
        String title,
        String content
) {
}

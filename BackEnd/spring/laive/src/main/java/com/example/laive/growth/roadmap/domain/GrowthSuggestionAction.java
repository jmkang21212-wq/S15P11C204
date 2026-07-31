package com.example.laive.growth.roadmap.domain;

public record GrowthSuggestionAction(
        long id,
        long suggestionId,
        int displayOrder,
        String title,
        String content
) {
}

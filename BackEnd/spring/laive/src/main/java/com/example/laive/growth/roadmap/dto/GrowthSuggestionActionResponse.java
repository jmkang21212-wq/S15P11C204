package com.example.laive.growth.roadmap.dto;

import com.example.laive.growth.roadmap.domain.GrowthSuggestionAction;

public record GrowthSuggestionActionResponse(
        long suggestionActionId,
        int displayOrder,
        String title,
        String content
) {
    static GrowthSuggestionActionResponse from(GrowthSuggestionAction value) {
        return new GrowthSuggestionActionResponse(
                value.id(), value.displayOrder(), value.title(), value.content()
        );
    }
}

package com.example.laive.growth.roadmap.domain;

import java.util.List;

public record GrowthSuggestionView(
        GrowthGoalSuggestion suggestion,
        List<GrowthSuggestionAction> actions
) {
    public GrowthSuggestionView {
        actions = List.copyOf(actions);
    }
}

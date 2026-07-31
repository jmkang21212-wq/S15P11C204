package com.example.laive.growth.roadmap.domain;

import java.util.List;

public record GrowthRoadmapView(
        GrowthRoadmap roadmap,
        List<GrowthSuggestionView> suggestions
) {
    public GrowthRoadmapView {
        suggestions = List.copyOf(suggestions);
    }
}

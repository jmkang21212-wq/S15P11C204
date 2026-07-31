package com.example.laive.growth.ai;

import java.util.HashSet;
import java.util.List;

public record GrowthActionResult(List<GeneratedGrowthAction> actions) {
    public GrowthActionResult {
        if (actions == null) {
            throw new IllegalArgumentException("Growth actions are required.");
        }
        actions = List.copyOf(actions);
        if (actions.isEmpty() || actions.size() > 3) {
            throw new IllegalArgumentException("Growth actions must contain 1-3 items.");
        }
        var titles = new HashSet<String>();
        if (actions.stream().anyMatch(action -> !titles.add(action.title().strip()))) {
            throw new IllegalArgumentException("Growth action titles must be unique.");
        }
    }
}

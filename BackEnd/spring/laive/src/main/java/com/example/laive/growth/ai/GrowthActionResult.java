package com.example.laive.growth.ai;

import java.util.HashSet;
import java.util.List;

public record GrowthActionResult(List<GeneratedGrowthAction> actions) {
    public GrowthActionResult {
        if (actions == null) {
            throw new IllegalArgumentException("성장 액션 목록은 필수입니다.");
        }
        actions = List.copyOf(actions);
        if (actions.isEmpty() || actions.size() > 3) {
            throw new IllegalArgumentException("성장 액션은 1~3개여야 합니다.");
        }
        var titles = new HashSet<String>();
        if (actions.stream().anyMatch(action -> !titles.add(action.title().strip()))) {
            throw new IllegalArgumentException("성장 액션 제목은 서로 달라야 합니다.");
        }
    }
}

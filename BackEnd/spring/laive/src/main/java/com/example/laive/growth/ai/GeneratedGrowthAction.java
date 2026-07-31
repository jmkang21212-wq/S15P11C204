package com.example.laive.growth.ai;

public record GeneratedGrowthAction(String title, String content) {
    public GeneratedGrowthAction {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("Growth action title must be 1-200 characters.");
        }
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("Growth action content must be 1-2000 characters.");
        }
    }
}

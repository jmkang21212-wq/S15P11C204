package com.example.laive.growth.ai;

public record GeneratedGrowthAction(String title, String content) {
    public GeneratedGrowthAction {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("성장 액션 제목은 1~200자여야 합니다.");
        }
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("성장 액션 내용은 1~2000자여야 합니다.");
        }
    }
}

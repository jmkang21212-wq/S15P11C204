package com.example.laive.report.ai;

public record InsightResult(String title, String content) {
    public InsightResult {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("인사이트 제목은 1~200자여야 합니다.");
        }
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("인사이트 내용은 1~2000자여야 합니다.");
        }
    }
}

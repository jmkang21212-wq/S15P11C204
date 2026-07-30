package com.example.laive.report.domain;

public record SentimentCounts(
        long analyzedChat,
        long joy,
        long excitement,
        long support,
        long surprise,
        long sadness,
        long anger,
        long confusion,
        long neutral
) {
    public SentimentCounts {
        if (analyzedChat < 0 || joy < 0 || excitement < 0 || support < 0 || surprise < 0
                || sadness < 0 || anger < 0 || confusion < 0 || neutral < 0) {
            throw new IllegalArgumentException("감정 건수는 음수일 수 없습니다.");
        }
        long sum = joy + excitement + support + surprise + sadness + anger + confusion + neutral;
        if (sum != analyzedChat) {
            throw new IllegalArgumentException("감정별 건수의 합은 analyzedChat과 같아야 합니다.");
        }
    }
}

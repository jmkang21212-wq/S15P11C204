package com.example.laive.report.ai;

public record TriggerNarrative(String narrative) {
    public TriggerNarrative {
        if (narrative == null || narrative.isBlank() || narrative.length() > 1000) {
            throw new IllegalArgumentException("트리거 설명은 1~1000자여야 합니다.");
        }
    }
}

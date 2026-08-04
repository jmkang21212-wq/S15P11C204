package com.example.laive.broadcast.session.domain;

/**
 * 방송 세션의 영속 상태다. DB CHECK 제약과 이름을 반드시 동일하게 유지한다.
 */
public enum BroadcastStatus {
    PREPARING,
    LIVE,
    ENDED,
    FAILED;

    public boolean isActive() {
        return this == PREPARING || this == LIVE;
    }

    public boolean isTerminal() {
        return this == ENDED || this == FAILED;
    }
}


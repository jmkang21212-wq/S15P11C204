package com.example.laive.broadcast.session.domain;

/** 플랫폼 계정별 방송 수집 lifecycle 상태다. */
public enum PlatformSessionStatus {
    PREPARING,
    LIVE,
    ENDED,
    FAILED;

    public boolean isTerminal() {
        return this == ENDED || this == FAILED;
    }
}

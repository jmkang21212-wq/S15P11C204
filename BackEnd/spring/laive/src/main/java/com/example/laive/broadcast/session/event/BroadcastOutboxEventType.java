package com.example.laive.broadcast.session.event;

/**
 * DB CHECK 제약과 이름을 공유하는 방송 세션 Outbox 이벤트 종류다.
 */
public enum BroadcastOutboxEventType {
    PREPARATION_REQUESTED,
    BROADCAST_STARTED,
    BROADCAST_ENDED
}


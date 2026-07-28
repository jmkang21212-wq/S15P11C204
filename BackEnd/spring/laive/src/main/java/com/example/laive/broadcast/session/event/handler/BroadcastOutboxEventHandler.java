package com.example.laive.broadcast.session.event.handler;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;

/**
 * 이벤트 종류별 후속 작업을 분리하고 재전달을 기본 동작으로 삼는다.
 */
public interface BroadcastOutboxEventHandler {

    BroadcastOutboxEventType supports();

    void handle(BroadcastOutboxEvent event);

    default void onDead(BroadcastOutboxEvent event) {
    }
}


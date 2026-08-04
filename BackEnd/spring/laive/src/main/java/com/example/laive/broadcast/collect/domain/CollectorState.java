package com.example.laive.broadcast.collect.domain;

/**
 * 실시간 콘솔의 {@code services.collector}에 노출하는 채팅 수집기 연결 상태다(명세 10.6).
 *
 * <p>수집기는 자동 재연결이 불가능해 재핸드셰이크로 복구하며 그 사이의 채팅은
 * 영구 유실된다. 수집이 멈춘 것을 스트리머가 알아야 하므로 화면에 내보낸다.
 */
public enum CollectorState {

    /** 구독까지 끝나 CHAT 이벤트를 받고 있다. */
    RUNNING,

    /** 연결이 끊겨 재핸드셰이크 중이다. 이 구간의 채팅은 유실된다. */
    RECONNECTING,

    /** 수집기가 이 세션을 돌리고 있지 않다. 하트비트가 끊긴 경우를 포함한다. */
    STOPPED
}

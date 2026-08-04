package com.example.laive.broadcast.collect.domain;

/**
 * 수집이 끊긴 이유다. {@code collection_gaps.reason} CHECK 제약(V9)과 값이 같아야 한다.
 */
public enum CollectionGapReason {

    /** socket.io 연결이 끊겼다. 가장 흔하다. */
    DISCONNECTED,

    /** 같은 인증 URL로 재연결을 시도해 서버가 auth fail로 끊었다. */
    AUTH_FAILED,

    /** 핸드셰이크는 됐으나 chat 구독 요청이 실패했다. */
    SUBSCRIBE_FAILED,

    /** 수집기 프로세스가 내려갔다. */
    COLLECTOR_STOPPED
}

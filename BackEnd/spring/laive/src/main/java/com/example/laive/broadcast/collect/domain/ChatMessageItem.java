package com.example.laive.broadcast.collect.domain;

import java.time.Instant;

/**
 * 수집기가 보낸 채팅 한 건이다(명세 13.3.1).
 *
 * @param viewerHash 수집기가 SHA-256으로 해시한 시청자 식별자. 플랫폼 원본 식별자는 전달받지 않는다
 * @param message    채팅 원문
 * @param sentAt     치지직 {@code messageTime}(epoch ms)에서 변환한 시각
 */
public record ChatMessageItem(String viewerHash, String message, Instant sentAt) {
}

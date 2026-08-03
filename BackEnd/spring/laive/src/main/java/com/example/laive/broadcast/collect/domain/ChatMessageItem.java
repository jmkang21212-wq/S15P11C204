package com.example.laive.broadcast.collect.domain;

import java.time.Instant;
import java.math.BigDecimal;

/**
 * 수집기가 보낸 채팅 한 건이다(명세 13.3.1).
 *
 * @param viewerHash     수집기가 SHA-256으로 해시한 시청자 식별자. 플랫폼 원본 식별자는 전달받지 않는다
 * @param message        채팅 원문
 * @param sentAt         치지직 {@code messageTime}(epoch ms)에서 변환한 시각
 * @param sentimentLabel 감정 코드(V8의 8종). 수집기는 보내지 않고 chat-ai 중계가 붙인다.
 *                       분류하지 못했으면 null — 원본 저장이 라벨에 볼모잡히지 않는다
 */
public record ChatMessageItem(
        String viewerHash,
        String message,
        Instant sentAt,
        String sentimentLabel,
        String platformMessageId,
        ChatMessageType messageType,
        BigDecimal amount,
        String currency,
        String providerEventType
) {

    public ChatMessageItem {
        messageType = messageType == null ? ChatMessageType.TEXT : messageType;
    }

    public ChatMessageItem(String viewerHash, String message, Instant sentAt) {
        this(viewerHash, message, sentAt, null, null, ChatMessageType.TEXT,
                null, null, null);
    }

    public ChatMessageItem(String viewerHash, String message, Instant sentAt, String sentimentLabel) {
        this(viewerHash, message, sentAt, sentimentLabel, null, ChatMessageType.TEXT,
                null, null, null);
    }

    public ChatMessageItem withSentimentLabel(String sentimentLabel) {
        return new ChatMessageItem(
                viewerHash, message, sentAt, sentimentLabel, platformMessageId,
                messageType, amount, currency, providerEventType);
    }
}

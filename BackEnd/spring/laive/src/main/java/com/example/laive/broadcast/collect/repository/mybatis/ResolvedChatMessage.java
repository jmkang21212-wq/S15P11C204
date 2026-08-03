package com.example.laive.broadcast.collect.repository.mybatis;

import java.time.Instant;
import java.math.BigDecimal;
import com.example.laive.broadcast.collect.domain.ChatMessageType;

/**
 * viewerHash를 viewer_profiles.id로 바꾼 뒤의 채팅 한 건이다.
 *
 * @param sentimentLabel 감정 코드. 분류하지 못한 건 null로 남는다
 */
public record ResolvedChatMessage(
        long viewerId,
        String message,
        Instant sentAt,
        String sentimentLabel,
        String platformMessageId,
        ChatMessageType messageType,
        BigDecimal amount,
        String currency,
        String providerEventType
) {
    public ResolvedChatMessage(
            long viewerId, String message, Instant sentAt, String sentimentLabel) {
        this(viewerId, message, sentAt, sentimentLabel, null, ChatMessageType.TEXT,
                null, null, null);
    }
}

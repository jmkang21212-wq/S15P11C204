package com.example.laive.broadcast.collect.dto.response;

/** Collector launch contract that does not expose provider-specific DB columns. */
public record PlatformCollectorSpecResponse(
        long platformSessionId,
        long sessionId,
        String platformCode,
        String accessToken,
        String externalAccountId,
        String channelName,
        String externalBroadcastId,
        String liveChatId,
        String watchUrl
) {
}

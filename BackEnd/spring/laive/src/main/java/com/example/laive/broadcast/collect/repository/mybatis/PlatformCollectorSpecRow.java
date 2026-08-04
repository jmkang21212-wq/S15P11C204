package com.example.laive.broadcast.collect.repository.mybatis;

/** Provider-neutral launch data for an external collector worker. */
public record PlatformCollectorSpecRow(
        long platformSessionId,
        long sessionId,
        long streamerId,
        long platformAccountId,
        String platformCode,
        String externalAccountId,
        String channelName,
        String externalBroadcastId,
        String liveChatId,
        String watchUrl
) {
}

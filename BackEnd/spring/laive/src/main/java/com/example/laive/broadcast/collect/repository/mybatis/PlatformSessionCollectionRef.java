package com.example.laive.broadcast.collect.repository.mybatis;

/** Identity and ownership needed to ingest one provider-specific collector stream. */
public record PlatformSessionCollectionRef(
        long platformSessionId,
        long sessionId,
        long streamerId,
        long platformAccountId,
        String platformCode
) {
}

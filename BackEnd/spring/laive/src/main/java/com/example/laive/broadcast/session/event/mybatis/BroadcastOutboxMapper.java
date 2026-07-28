package com.example.laive.broadcast.session.event.mybatis;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BroadcastOutboxMapper {

    int insert(
            @Param("eventId") UUID eventId,
            @Param("aggregateId") long aggregateId,
            @Param("eventType") BroadcastOutboxEventType eventType,
            @Param("deduplicationKey") String deduplicationKey,
            @Param("payload") String payload,
            @Param("availableAt") Instant availableAt
    );

    List<BroadcastOutboxEvent> claim(
            @Param("workerId") String workerId,
            @Param("now") Instant now,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("limit") int limit
    );

    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("publishedAt") Instant publishedAt
    );

    int scheduleRetry(
            @Param("eventId") UUID eventId,
            @Param("attempts") int attempts,
            @Param("availableAt") Instant availableAt,
            @Param("lastError") String lastError
    );

    int markDead(
            @Param("eventId") UUID eventId,
            @Param("attempts") int attempts,
            @Param("lastError") String lastError
    );
}


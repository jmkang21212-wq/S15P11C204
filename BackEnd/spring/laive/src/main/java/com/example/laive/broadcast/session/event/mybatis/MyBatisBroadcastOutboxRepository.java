package com.example.laive.broadcast.session.event.mybatis;

import com.example.laive.broadcast.session.event.BroadcastOutboxEvent;
import com.example.laive.broadcast.session.event.BroadcastOutboxEventType;
import com.example.laive.broadcast.session.event.BroadcastOutboxRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisBroadcastOutboxRepository implements BroadcastOutboxRepository {

    private final BroadcastOutboxMapper mapper;

    public MyBatisBroadcastOutboxRepository(BroadcastOutboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean save(
            UUID eventId,
            long aggregateId,
            BroadcastOutboxEventType eventType,
            String deduplicationKey,
            String payload,
            Instant availableAt
    ) {
        return mapper.insert(
                eventId,
                aggregateId,
                eventType,
                deduplicationKey,
                payload,
                availableAt
        ) == 1;
    }

    @Override
    public List<BroadcastOutboxEvent> claim(
            String workerId,
            Instant now,
            Instant lockedUntil,
            int limit
    ) {
        return mapper.claim(workerId, now, lockedUntil, limit);
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        mapper.markPublished(eventId, publishedAt);
    }

    @Override
    public void scheduleRetry(
            UUID eventId,
            int attempts,
            Instant availableAt,
            String lastError
    ) {
        mapper.scheduleRetry(eventId, attempts, availableAt, lastError);
    }

    @Override
    public void markDead(UUID eventId, int attempts, String lastError) {
        mapper.markDead(eventId, attempts, lastError);
    }
}


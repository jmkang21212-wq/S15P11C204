package com.example.laive.media.repository.mybatis;

import com.example.laive.media.domain.RecordingStatus;
import com.example.laive.media.repository.RecordingRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class MyBatisRecordingRepository implements RecordingRepository {

    private final RecordingMapper mapper;

    public MyBatisRecordingRepository(RecordingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isSessionOwnedBy(long streamerId, long sessionId) {
        return mapper.existsSession(streamerId, sessionId);
    }

    @Override
    public void start(long sessionId, Instant startedAt, Instant baseWallClock, long baseTimecodeMs) {
        mapper.upsertStart(sessionId, startedAt, baseWallClock, baseTimecodeMs);
    }

    @Override
    public int updateBase(long sessionId, Instant baseWallClock, long baseTimecodeMs) {
        return mapper.updateBase(sessionId, baseWallClock, baseTimecodeMs);
    }

    @Override
    public int finish(long sessionId, Instant endedAt, String outputPath, RecordingStatus status) {
        return mapper.finish(sessionId, endedAt, outputPath, status);
    }
}

package com.example.laive.media.repository.mybatis;

import com.example.laive.media.domain.RecordingStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface RecordingMapper {

    boolean existsSession(
            @Param("streamerId") long streamerId,
            @Param("sessionId") long sessionId
    );

    int upsertStart(
            @Param("sessionId") long sessionId,
            @Param("startedAt") Instant startedAt,
            @Param("baseWallClock") Instant baseWallClock,
            @Param("baseTimecodeMs") long baseTimecodeMs
    );

    int updateBase(
            @Param("sessionId") long sessionId,
            @Param("baseWallClock") Instant baseWallClock,
            @Param("baseTimecodeMs") long baseTimecodeMs
    );

    int finish(
            @Param("sessionId") long sessionId,
            @Param("endedAt") Instant endedAt,
            @Param("outputPath") String outputPath,
            @Param("status") RecordingStatus status
    );
}

package com.example.laive.media.repository;

import com.example.laive.media.domain.RecordingStatus;

import java.time.Instant;

/**
 * 녹화본 위치와 시간축 기준점의 영속성 Port다.
 */
public interface RecordingRepository {

    /** 세션이 존재하고 그 스트리머의 것인지 확인한다. */
    boolean isSessionOwnedBy(long streamerId, long sessionId);

    /**
     * 녹화 시작을 기록한다. 같은 세션에 두 번 오면 기준점만 갱신한다 —
     * 앱이 응답을 못 받고 재시도하는 것이 정상 흐름이라 여기서 409를 내면 안 된다.
     */
    void start(long sessionId, Instant startedAt, Instant baseWallClock, long baseTimecodeMs);

    /** 기준점을 갱신한다. 대상이 없으면 0을 돌려준다. */
    int updateBase(long sessionId, Instant baseWallClock, long baseTimecodeMs);

    /** 녹화 종료를 기록한다. 대상이 없으면 0을 돌려준다. */
    int finish(long sessionId, Instant endedAt, String outputPath, RecordingStatus status);
}

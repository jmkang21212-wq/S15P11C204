package com.example.laive.media.service;

import com.example.laive.media.domain.RecordingStatus;
import com.example.laive.media.exception.MediaException;
import com.example.laive.media.repository.RecordingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Electron이 보고하는 OBS 녹화 상태를 기록한다(CLIP_SPEC.md §6.1~6.3).
 *
 * <p>서버는 녹화를 하지도, 보고된 경로를 열지도 않는다. 그 경로는 다른 컴퓨터의
 * 것이고 클립 작업을 내려줄 때 그대로 돌려주기 위해서만 보관한다.
 */
@Service
public class RecordingService {

    private final RecordingRepository repository;

    public RecordingService(RecordingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void start(long streamerId, long sessionId, Instant startedAt,
                      Instant baseWallClock, long baseTimecodeMs) {
        requireOwnership(streamerId, sessionId);
        repository.start(sessionId, startedAt, baseWallClock, baseTimecodeMs);
    }

    /**
     * 시간 기준점을 갱신한다(60초 주기).
     *
     * <p>대상이 없어도 예외를 던지지 않는다. 녹화 종료 보고가 먼저 처리되고 마지막
     * 갱신이 뒤늦게 도착하는 순서 역전이 정상적으로 일어나는데, 여기서 404를 내면
     * 앱이 이미 끝난 녹화를 계속 재시도한다.
     */
    @Transactional
    public void updateBase(long streamerId, long sessionId, Instant baseWallClock, long baseTimecodeMs) {
        requireOwnership(streamerId, sessionId);
        repository.updateBase(sessionId, baseWallClock, baseTimecodeMs);
    }

    @Transactional
    public void finish(long streamerId, long sessionId, Instant endedAt,
                       String outputPath, RecordingStatus status) {
        requireOwnership(streamerId, sessionId);
        if (repository.finish(sessionId, endedAt, outputPath, status) == 0) {
            throw MediaException.recordingNotFound();
        }
    }

    private void requireOwnership(long streamerId, long sessionId) {
        if (!repository.isSessionOwnedBy(streamerId, sessionId)) {
            throw MediaException.sessionNotFound();
        }
    }
}

package com.example.laive.media.domain;

import java.time.Instant;

/**
 * 클립 작업 후보 한 건이다. 오프셋 계산 전의 원재료라 시각이 여러 형태로 섞여 있다.
 *
 * <p>MyBatis가 생성자로 매핑하므로 <b>필드 순서가 SELECT 컬럼 순서와 일치해야 한다</b>.
 * 어긋나면 예외 없이 조용히 잘못 채워진다.
 *
 * @param triggerStartedAt 구간 시작의 실제 시각. {@code highlights.trigger_id}가
 *                         {@code ON DELETE SET NULL}이라 NULL일 수 있고, 그때는
 *                         {@code sessionStartedAt + startMs}로 폴백한다
 */
public record ClipJobRow(
        long highlightId,
        long sessionId,
        String sessionTitle,
        Instant sessionStartedAt,
        Instant triggerStartedAt,
        long startMs,
        long endMs,
        String recordingPath,
        Instant baseWallClock,
        long baseTimecodeMs
) {

    public RecordingTimebase timebase() {
        return new RecordingTimebase(baseWallClock, baseTimecodeMs);
    }

    public long durationMs() {
        return endMs - startMs;
    }
}

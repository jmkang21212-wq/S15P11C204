package com.example.laive.media.domain;

import java.time.Instant;

/**
 * 녹화 타임코드와 실제 시각을 잇는 기준점이다.
 *
 * <p>"녹화 시작 시각"을 저장해 두고 빼는 방식을 쓰지 않는 이유는 드리프트다.
 * OBS 녹화는 프레임 드랍·인코더 지연으로 실제 경과 시각과 서서히 어긋나서,
 * 시작점에서 멀어질수록 오차가 누적된다. Electron이 60초마다 갱신하는 이 기준점을
 * 쓰면 가장 가까운 지점에서 환산하므로 누적 오차가 사라진다.
 *
 * @param wallClock  이 기준점을 읽은 실제 시각
 * @param timecodeMs 그때의 녹화 경과 시간
 */
public record RecordingTimebase(Instant wallClock, long timecodeMs) {

    public RecordingTimebase {
        if (wallClock == null) {
            throw new IllegalArgumentException("기준점 시각이 없습니다.");
        }
        if (timecodeMs < 0) {
            throw new IllegalArgumentException("녹화 경과 시간이 음수입니다: " + timecodeMs);
        }
    }

    /** 주어진 실제 시각이 녹화본의 몇 밀리초 지점인지 환산한다. 녹화 이전이면 음수다. */
    public long toTimecodeMs(Instant at) {
        return java.time.Duration.between(wallClock, at).toMillis() + timecodeMs;
    }
}

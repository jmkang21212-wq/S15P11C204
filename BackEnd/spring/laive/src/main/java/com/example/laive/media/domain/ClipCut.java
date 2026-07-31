package com.example.laive.media.domain;

/**
 * 녹화본에서 잘라낼 구간이다. 기준은 <b>녹화 타임코드</b>이지 방송 오프셋이 아니다.
 *
 * <p>Electron은 이 값을 ffmpeg의 {@code -ss}/{@code -t}에 그대로 넣는다.
 * 송출 지연 보정은 이미 끝나 있으므로 클라이언트에서 다시 계산하지 않는다.
 */
public record ClipCut(long startMs, long durationMs) {

    public ClipCut {
        if (startMs < 0) {
            throw new IllegalArgumentException("클립 시작이 음수입니다: " + startMs);
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("클립 길이가 0 이하입니다: " + durationMs);
        }
    }

    public double startSec() {
        return startMs / 1000.0;
    }

    public double durationSec() {
        return durationMs / 1000.0;
    }
}

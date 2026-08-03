package com.example.laive.media.domain;

/**
 * Electron이 그대로 실행할 수 있는 형태의 클립 작업이다.
 *
 * <p>{@code cut}은 송출 지연 보정까지 끝난 <b>녹화 타임코드</b>다. 클라이언트는
 * 이 값을 ffmpeg의 {@code -ss}/{@code -t}에 넣기만 하고 다시 계산하지 않는다.
 */
public record ClipJob(
        long highlightId,
        long sessionId,
        String sessionTitle,
        String recordingPath,
        ClipCut cut
) {
}

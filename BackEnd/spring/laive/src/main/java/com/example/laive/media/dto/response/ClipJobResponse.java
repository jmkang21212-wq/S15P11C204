package com.example.laive.media.dto.response;

import com.example.laive.media.domain.ClipJob;

/**
 * 앱이 ffmpeg에 그대로 넣을 수 있는 형태의 작업이다.
 *
 * <p>{@code clipStartSec}은 송출 지연 보정까지 끝난 <b>녹화 타임코드</b>다.
 * 클라이언트에서 다시 계산하지 말 것 — 보정값이 서버 설정이라 앱 버전마다 결과가
 * 갈리면 "클립이 이상하다"의 원인 추적이 불가능해진다.
 */
public record ClipJobResponse(
        long highlightId,
        long sessionId,
        String sessionTitle,
        String recordingPath,
        double clipStartSec,
        double clipDurationSec
) {
    public static ClipJobResponse from(ClipJob job) {
        return new ClipJobResponse(
                job.highlightId(), job.sessionId(), job.sessionTitle(), job.recordingPath(),
                job.cut().startSec(), job.cut().durationSec()
        );
    }
}

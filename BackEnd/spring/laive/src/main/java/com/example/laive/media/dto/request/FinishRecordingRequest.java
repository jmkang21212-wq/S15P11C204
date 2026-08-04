package com.example.laive.media.dto.request;

import com.example.laive.media.domain.RecordingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 녹화 종료 보고다.
 *
 * @param outputPath 스트리머 PC의 최종 파일 경로. 서버는 이 값을 해석하지 않고
 *                   클립 작업을 내려줄 때 그대로 돌려준다
 * @param status     COMPLETED 또는 FAILED. FAILED면 그 세션의 클립 작업은 생기지 않는다
 */
public record FinishRecordingRequest(
        @NotNull Instant endedAt,
        @NotBlank String outputPath,
        @NotNull RecordingStatus status
) {
}

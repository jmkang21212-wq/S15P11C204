package com.example.laive.media.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * 녹화 시작 보고다. 기준점은 시작 직후 읽은 값이라 보통 0에 가깝다.
 */
public record StartRecordingRequest(
        @NotNull Instant startedAt,
        @NotNull Instant baseWallClock,
        @NotNull @PositiveOrZero Long baseTimecodeMs
) {
}

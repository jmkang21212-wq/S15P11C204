package com.example.laive.media.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/**
 * 시간 기준점 갱신이다(60초 주기). 두 값은 <b>같은 순간에</b> 읽어야 한다 —
 * 사이가 벌어지면 그만큼 오차가 그대로 클립 위치에 옮겨간다.
 */
public record UpdateRecordingBaseRequest(
        @NotNull Instant baseWallClock,
        @NotNull @PositiveOrZero Long baseTimecodeMs
) {
}

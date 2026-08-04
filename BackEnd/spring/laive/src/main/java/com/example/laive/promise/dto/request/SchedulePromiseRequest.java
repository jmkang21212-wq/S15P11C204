package com.example.laive.promise.dto.request;

import com.example.laive.promise.domain.SchedulePromiseCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 약속 → 일정 전환 API 입력이다.
 *
 * <p>title은 선택이다. 안 보내면 약속 content를 제목 길이로 잘라 쓴다 — 사용자가 확정 화면에서
 * 제목을 따로 손보지 않는 경우가 대부분이기 때문이다. 200자를 넘겨 보내면 400으로 거부한다
 * (조용히 자르면 사용자가 보낸 것과 저장된 것이 달라진다).
 *
 * <p>eventType과 platformAccountId가 없는 것은 의도된 것이다. 전환은 항상 방송 일정이고,
 * 계정은 서버가 고른다.
 */
public record SchedulePromiseRequest(
        @NotNull(message = "필수 값입니다.")
        Instant startAt,

        @NotNull(message = "필수 값입니다.")
        Instant endAt,

        @Size(min = 1, max = 200, message = "1자 이상 200자 이하여야 합니다.")
        String title,

        @Size(max = 2000, message = "2000자 이하여야 합니다.")
        String memo
) {
    public SchedulePromiseCommand toCommand() {
        return new SchedulePromiseCommand(startAt, endAt, title, memo);
    }
}

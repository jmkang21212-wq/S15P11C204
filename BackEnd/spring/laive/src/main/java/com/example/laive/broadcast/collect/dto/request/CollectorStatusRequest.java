package com.example.laive.broadcast.collect.dto.request;

import com.example.laive.broadcast.collect.domain.CollectorState;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 수집기 연결 상태 보고다. 하트비트를 겸하므로 상태가 바뀌지 않아도 주기적으로 온다.
 */
public record CollectorStatusRequest(
        @NotNull CollectorState status,
        @NotNull Instant occurredAt
) {
}

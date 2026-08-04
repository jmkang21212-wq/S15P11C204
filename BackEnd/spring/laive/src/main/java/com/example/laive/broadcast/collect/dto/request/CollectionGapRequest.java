package com.example.laive.broadcast.collect.dto.request;

import com.example.laive.broadcast.collect.domain.CollectionGap;
import com.example.laive.broadcast.collect.domain.CollectionGapReason;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 수집 공백 구간 보고다.
 *
 * <p>끊긴 시점에 {@code endedAt} 없이 먼저 보내고, 재핸드셰이크로 복구한 뒤
 * 같은 {@code startedAt}으로 다시 보내 구간을 닫는다.
 */
public record CollectionGapRequest(
        @NotNull Instant startedAt,
        Instant endedAt,
        @NotNull CollectionGapReason reason
) {

    public CollectionGap toDomain() {
        return new CollectionGap(startedAt, endedAt, reason);
    }
}

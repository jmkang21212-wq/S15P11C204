package com.example.laive.broadcast.collect.domain;

import java.time.Instant;

/**
 * 채팅 수집이 끊긴 구간이다.
 *
 * @param startedAt 끊김을 감지한 시각
 * @param endedAt   구독을 회복한 시각. 아직 복구 전이면 {@code null}
 * @param reason    끊긴 이유
 */
public record CollectionGap(Instant startedAt, Instant endedAt, CollectionGapReason reason) {
}

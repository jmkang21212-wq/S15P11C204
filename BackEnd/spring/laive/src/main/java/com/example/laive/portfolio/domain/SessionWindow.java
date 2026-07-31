package com.example.laive.portfolio.domain;

import java.time.Instant;

/**
 * primaryTimeRange 계산에 쓰는 최근 종료 방송의 시작·종료 시각이다.
 *
 * <p>status='ENDED'만 조회하므로 endedAt은 항상 있다 (DB CHECK가 보장).
 */
public record SessionWindow(
        Instant startedAt,
        Instant endedAt
) {
}

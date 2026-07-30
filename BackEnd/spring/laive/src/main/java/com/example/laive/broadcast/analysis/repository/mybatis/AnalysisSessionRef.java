package com.example.laive.broadcast.analysis.repository.mybatis;

import java.time.Instant;

/**
 * 발화 저장에 필요한 세션 최소 정보다.
 *
 * <p>startedAt은 벽시계 시각을 방송 시작 기준 밀리초로 바꾸는 기준점이고,
 * streamerId는 PROMISES가 NOT NULL로 요구한다.
 */
public record AnalysisSessionRef(
        long sessionId,
        long streamerId,
        Instant startedAt
) {
}

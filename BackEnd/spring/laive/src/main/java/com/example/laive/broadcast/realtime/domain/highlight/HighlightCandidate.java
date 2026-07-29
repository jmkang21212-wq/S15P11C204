package com.example.laive.broadcast.realtime.domain.highlight;

import java.time.Instant;

/**
 * 상태기계가 확정한 채팅 급증 구간이다. 저장 시 구간 내 통계를 집계해
 * {@code triggers} 한 행이 된다.
 *
 * @param startedAt      진입 확정 틱에서 리드만큼 앞으로 당긴 시작점
 * @param endedAt        이탈 첫 틱 시각, 또는 강제 확정 시각
 * @param peakZ          구간 내 z 최댓값
 * @param peakEngagement 구간 내 engagement 최댓값
 */
public record HighlightCandidate(
        Instant startedAt,
        Instant endedAt,
        double peakZ,
        double peakEngagement
) {
}

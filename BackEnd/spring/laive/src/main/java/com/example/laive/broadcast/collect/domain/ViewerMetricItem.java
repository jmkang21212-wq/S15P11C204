package com.example.laive.broadcast.collect.domain;

import java.time.Instant;

/**
 * 시청자 수 측정 한 건이다(명세 13.3.2).
 */
public record ViewerMetricItem(Instant measuredAt, int viewerCount) {
}

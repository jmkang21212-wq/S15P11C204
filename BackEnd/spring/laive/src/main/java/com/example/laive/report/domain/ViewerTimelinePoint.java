package com.example.laive.report.domain;

import java.time.Instant;

public record ViewerTimelinePoint(Instant measuredAt, int viewerCount) {
}

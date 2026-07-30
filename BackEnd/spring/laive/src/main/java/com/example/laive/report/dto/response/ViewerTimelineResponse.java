package com.example.laive.report.dto.response;

import com.example.laive.report.domain.ViewerTimelinePoint;

import java.time.Instant;

public record ViewerTimelineResponse(Instant measuredAt, int viewerCount) {
    public static ViewerTimelineResponse from(ViewerTimelinePoint point) {
        return new ViewerTimelineResponse(point.measuredAt(), point.viewerCount());
    }
}

package com.example.laive.growth.roadmap.dto;

import com.example.laive.growth.roadmap.domain.GrowthRoadmapView;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.domain.RoadmapStatus;

import java.time.Instant;
import java.util.List;

public record GrowthRoadmapResponse(
        Long roadmapId,
        RoadmapStatus status,
        RoadmapQuality quality,
        boolean stale,
        String promptVersion,
        Instant generatedAt,
        Long sourceReportId,
        String errorCode,
        List<GrowthGoalSuggestionResponse> suggestions
) {
    public static GrowthRoadmapResponse notRequested() {
        return new GrowthRoadmapResponse(
                null, RoadmapStatus.NOT_REQUESTED, null, false,
                null, null, null, null, List.of()
        );
    }

    public static GrowthRoadmapResponse from(GrowthRoadmapView view) {
        var roadmap = view.roadmap();
        return new GrowthRoadmapResponse(
                roadmap.id(), roadmap.status(), roadmap.quality(), roadmap.stale(),
                roadmap.promptVersion(), roadmap.generatedAt(), roadmap.sourceReportId(),
                roadmap.generationError(),
                view.suggestions().stream().map(GrowthGoalSuggestionResponse::from).toList()
        );
    }
}

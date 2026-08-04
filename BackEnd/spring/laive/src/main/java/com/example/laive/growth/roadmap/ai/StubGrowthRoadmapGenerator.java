package com.example.laive.growth.roadmap.ai;

import java.util.Objects;
import java.util.function.Function;

public final class StubGrowthRoadmapGenerator implements GrowthRoadmapGenerator {
    private final Function<GrowthRoadmapInput, GrowthRoadmapResult> response;

    public StubGrowthRoadmapGenerator(
            Function<GrowthRoadmapInput, GrowthRoadmapResult> response
    ) {
        this.response = Objects.requireNonNull(response);
    }

    @Override
    public GrowthRoadmapResult generate(GrowthRoadmapInput input) {
        return response.apply(input);
    }
}

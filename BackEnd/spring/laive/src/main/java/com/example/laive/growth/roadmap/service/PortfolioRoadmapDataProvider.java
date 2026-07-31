package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.roadmap.ai.PortfolioRoadmapSnapshot;

import java.util.Optional;

public interface PortfolioRoadmapDataProvider {
    Optional<PortfolioRoadmapSnapshot> find(long streamerId);
}

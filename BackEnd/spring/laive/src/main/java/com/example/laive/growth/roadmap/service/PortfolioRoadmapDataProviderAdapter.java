package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.roadmap.ai.PortfolioRoadmapSnapshot;
import com.example.laive.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PortfolioRoadmapDataProviderAdapter implements PortfolioRoadmapDataProvider {
    private final PortfolioRepository repository;

    public PortfolioRoadmapDataProviderAdapter(PortfolioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PortfolioRoadmapSnapshot> find(long streamerId) {
        return repository.findByStreamerId(streamerId).map(portfolio -> {
            var performance = repository.findPerformanceSnapshot(streamerId);
            var platforms = repository.findPlatformAccounts(streamerId).stream()
                    .map(account -> account.platform() + ":" + account.channelName())
                    .toList();
            var tags = repository.findTags(streamerId).stream()
                    .map(tag -> tag.name())
                    .toList();
            var highlights = repository.findFeaturedHighlights(portfolio.portfolioId()).stream()
                    .map(value -> value.highlightId() + ":" +
                            (value.clipUrl() == null ? "clip-pending" : value.clipUrl()))
                    .toList();
            return new PortfolioRoadmapSnapshot(
                    portfolio.title(), portfolio.introduction(), platforms, tags,
                    performance.avgViewers(), performance.peakViewers(),
                    performance.broadcastsPerWeek(), performance.totalBroadcastCount(),
                    performance.totalChat(), highlights
            );
        });
    }
}

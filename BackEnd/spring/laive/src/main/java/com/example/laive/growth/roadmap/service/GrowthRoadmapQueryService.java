package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.roadmap.domain.GrowthRoadmapView;
import com.example.laive.growth.roadmap.domain.GrowthSuggestionAction;
import com.example.laive.growth.roadmap.domain.GrowthSuggestionView;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GrowthRoadmapQueryService {
    private final GrowthRoadmapRepository repository;

    public GrowthRoadmapQueryService(GrowthRoadmapRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<GrowthRoadmapView> find(long streamerId) {
        return repository.findRoadmap(streamerId).map(roadmap -> {
            var suggestions = repository.findCurrentSuggestions(roadmap.id());
            List<Long> ids = suggestions.stream().map(value -> value.id()).toList();
            Map<Long, List<GrowthSuggestionAction>> actions =
                    repository.findSuggestionActions(ids).stream()
                            .collect(Collectors.groupingBy(
                                    GrowthSuggestionAction::suggestionId
                            ));
            return new GrowthRoadmapView(
                    roadmap,
                    suggestions.stream()
                            .map(value -> new GrowthSuggestionView(
                                    value, actions.getOrDefault(value.id(), List.of())
                            ))
                            .toList()
            );
        });
    }
}

package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.roadmap.config.GrowthRoadmapAiProperties;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GrowthRoadmapPromptVersionReconciler {
    private final GrowthRoadmapRepository repository;
    private final GrowthRoadmapTransactionService transactions;
    private final GrowthRoadmapAiProperties properties;

    public GrowthRoadmapPromptVersionReconciler(
            GrowthRoadmapRepository repository,
            GrowthRoadmapTransactionService transactions,
            GrowthRoadmapAiProperties properties
    ) {
        this.repository = repository;
        this.transactions = transactions;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${laive.growth.roadmap-ai.reconcile-initial-delay:PT1M}",
            fixedDelayString = "${laive.growth.roadmap-ai.reconcile-delay:PT6H}"
    )
    public void reconcile() {
        if (!properties.enabled()) return;
        repository.findStreamersNeedingPromptVersion(
                properties.promptVersion(), 100
        ).forEach(transactions::requestForPromptVersion);
    }
}

package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.roadmap.ai.GrowthRoadmapGenerationException;
import com.example.laive.growth.roadmap.ai.GrowthRoadmapGenerator;
import org.springframework.stereotype.Service;

@Service
public class GrowthRoadmapGenerationService {
    private final GrowthRoadmapTransactionService transactions;
    private final GrowthRoadmapInputService inputService;
    private final GrowthRoadmapGenerator generator;

    public GrowthRoadmapGenerationService(
            GrowthRoadmapTransactionService transactions,
            GrowthRoadmapInputService inputService,
            GrowthRoadmapGenerator generator
    ) {
        this.transactions = transactions;
        this.inputService = inputService;
        this.generator = generator;
    }

    public void generate(long generationId, long streamerId, String promptVersion) {
        var generation = transactions.begin(generationId).orElse(null);
        if (generation == null) return;
        try {
            var input = inputService.build(streamerId, promptVersion);
            var result = generator.generate(input);
            transactions.complete(generation, input, result);
        } catch (GrowthRoadmapGenerationException exception) {
            if (exception.retryable()) {
                transactions.retry(generationId, normalized(exception));
                throw exception;
            }
            transactions.fail(generationId, normalized(exception));
        } catch (RuntimeException exception) {
            transactions.retry(generationId, normalized(exception));
            throw exception;
        }
    }

    public void dead(long generationId) {
        transactions.fail(generationId, "OUTBOX_DEAD");
    }

    private String normalized(RuntimeException exception) {
        String value = exception.getClass().getSimpleName();
        return value.length() <= 100 ? value : value.substring(0, 100);
    }
}

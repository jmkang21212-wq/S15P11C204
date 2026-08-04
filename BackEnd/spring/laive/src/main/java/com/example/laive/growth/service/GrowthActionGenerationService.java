package com.example.laive.growth.service;

import com.example.laive.growth.ai.GrowthActionGenerator;
import org.springframework.stereotype.Service;

@Service
public class GrowthActionGenerationService {
    private final GrowthActionTransactionService transactions;
    private final GrowthActionInputService inputService;
    private final GrowthActionGenerator generator;

    public GrowthActionGenerationService(
            GrowthActionTransactionService transactions,
            GrowthActionInputService inputService,
            GrowthActionGenerator generator
    ) {
        this.transactions = transactions;
        this.inputService = inputService;
        this.generator = generator;
    }

    public void generate(long generationId) {
        var generation = transactions.begin(generationId).orElse(null);
        if (generation == null) {
            return;
        }
        try {
            var input = inputService.build(generationId);
            var result = generator.generate(input);
            transactions.complete(generation, input, result);
        } catch (GrowthActionInputService.InsufficientGrowthActionDataException exception) {
            transactions.fail(generationId, "INSUFFICIENT_DATA");
        } catch (RuntimeException exception) {
            transactions.retry(generationId, normalized(exception));
            throw exception;
        }
    }

    public void dead(long generationId, String errorCode) {
        transactions.fail(generationId, errorCode);
    }

    private String normalized(RuntimeException exception) {
        String name = exception.getClass().getSimpleName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}

package com.example.laive.growth.service;

import com.example.laive.growth.ai.GeneratedGrowthAction;
import com.example.laive.growth.ai.GrowthActionGenerator;
import com.example.laive.growth.ai.GrowthActionInput;
import com.example.laive.growth.ai.GrowthActionResult;
import com.example.laive.growth.domain.GrowthActionGeneration;
import com.example.laive.growth.domain.GrowthActionJobStatus;
import com.example.laive.growth.domain.GrowthActionTriggerType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthActionGenerationServiceTest {
    @Test
    void generatesAndAtomicallyCompletesAClaimedJob() {
        GrowthActionTransactionService transactions = mock(GrowthActionTransactionService.class);
        GrowthActionInputService inputs = mock(GrowthActionInputService.class);
        GrowthActionGenerator generator = mock(GrowthActionGenerator.class);
        GrowthActionGeneration generation = generation();
        GrowthActionInput input = mock(GrowthActionInput.class);
        GrowthActionResult result = new GrowthActionResult(List.of(
                new GeneratedGrowthAction("다음 방송 액션", "다음 방송에서 실행하고 지표를 확인합니다.")
        ));
        when(transactions.begin(31L)).thenReturn(Optional.of(generation));
        when(inputs.build(31L)).thenReturn(input);
        when(generator.generate(input)).thenReturn(result);

        new GrowthActionGenerationService(transactions, inputs, generator).generate(31L);

        verify(transactions).complete(generation, input, result);
    }

    private GrowthActionGeneration generation() {
        return new GrowthActionGeneration(
                31, 21, 51L, 2, GrowthActionTriggerType.REPORT_COMPLETED,
                GrowthActionJobStatus.PROCESSING, "growth-action-v1", "key", null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:01Z"), null, null
        );
    }
}

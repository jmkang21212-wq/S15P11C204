package com.example.laive.growth.service;

import com.example.laive.growth.domain.GrowthGoal;
import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.domain.GrowthMetricUnit;
import com.example.laive.growth.exception.GrowthException;
import com.example.laive.growth.repository.GrowthGoalCommandRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthGoalCommandServiceTest {
    @Test
    void createsGoalAfterLockAndOverlapCheck() {
        GrowthGoalCommandRepository repository = mock(GrowthGoalCommandRepository.class);
        when(repository.insert(any())).thenReturn(21L);
        GrowthGoalCommandService service = new GrowthGoalCommandService(repository);
        var command = new CreateGrowthGoalCommand(
                GrowthMetricType.AVG_VIEWERS,
                new BigDecimal("3000"),
                GrowthMetricUnit.VIEWERS,
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-10-31")
        );

        service.create(101L, command);

        verify(repository).lockScope(101L, GrowthMetricType.AVG_VIEWERS);
        verify(repository).insert(any(GrowthGoal.class));
    }

    @Test
    void rejectsOverlappingActiveGoal() {
        GrowthGoalCommandRepository repository = mock(GrowthGoalCommandRepository.class);
        when(repository.existsOverlappingActive(any(Long.class), any(), any(), any(), any()))
                .thenReturn(true);
        GrowthGoalCommandService service = new GrowthGoalCommandService(repository);

        assertThatThrownBy(() -> service.create(
                101L,
                new CreateGrowthGoalCommand(
                        GrowthMetricType.TOTAL_CHAT,
                        BigDecimal.TEN,
                        GrowthMetricUnit.MESSAGES,
                        LocalDate.parse("2026-08-01"),
                        LocalDate.parse("2026-08-31")
                )
        )).isInstanceOf(GrowthException.class)
                .extracting("code").isEqualTo("GROWTH_GOAL_STATE_CONFLICT");
    }
}

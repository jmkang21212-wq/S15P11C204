package com.example.laive.growth.ai;

import com.example.laive.growth.domain.GrowthAction;
import com.example.laive.growth.domain.GrowthGoal;

import java.math.BigDecimal;
import java.util.List;

public record GrowthActionInput(
        GrowthGoal goal,
        BigDecimal currentValue,
        BigDecimal progressRate,
        List<RecentGrowthReport> recentReports,
        List<GrowthAction> previousActions,
        Long sourceReportId,
        String promptVersion,
        String inputFingerprint
) {
    public GrowthActionInput {
        recentReports = List.copyOf(recentReports);
        previousActions = List.copyOf(previousActions);
    }
}

package com.example.laive.growth.service;

import com.example.laive.growth.ai.GrowthActionInput;
import com.example.laive.growth.domain.GrowthGoalMetrics;
import com.example.laive.growth.domain.GrowthMath;
import com.example.laive.growth.repository.GrowthActionRepository;
import com.example.laive.growth.repository.GrowthGoalQueryRepository;
import com.example.laive.growth.repository.GrowthReportQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class GrowthActionInputService {
    private final GrowthActionRepository actionRepository;
    private final GrowthGoalQueryRepository goalRepository;
    private final GrowthReportQueryRepository reportRepository;

    public GrowthActionInputService(
            GrowthActionRepository actionRepository,
            GrowthGoalQueryRepository goalRepository,
            GrowthReportQueryRepository reportRepository
    ) {
        this.actionRepository = actionRepository;
        this.goalRepository = goalRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public GrowthActionInput build(long generationId) {
        var generation = actionRepository.findGeneration(generationId).orElseThrow();
        var goal = actionRepository.findGoal(generation.growthGoalId()).orElseThrow();
        var reports = actionRepository.findRecentReports(goal.id(), 5);
        if (reports.isEmpty()) {
            throw new InsufficientGrowthActionDataException();
        }
        GrowthGoalMetrics metrics = reportRepository
                .findGoalMetrics(goal.streamerId(), List.of(goal.id()))
                .stream().findFirst().orElse(null);
        BigDecimal current = metrics == null || metrics.valueFor(goal.metricType()) == null
                ? BigDecimal.ZERO : metrics.valueFor(goal.metricType());
        var previous = goalRepository.findActions(List.of(goal.id()));
        String canonical = goal.id() + "|" + goal.revision() + "|" + current.toPlainString()
                + "|" + reports + "|" + previous + "|" + generation.promptVersion();
        return new GrowthActionInput(
                goal, current, GrowthMath.progress(current, goal.targetValue()),
                reports, previous, generation.sourceReportId(), generation.promptVersion(),
                sha256(canonical)
        );
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static final class InsufficientGrowthActionDataException extends RuntimeException {
    }
}

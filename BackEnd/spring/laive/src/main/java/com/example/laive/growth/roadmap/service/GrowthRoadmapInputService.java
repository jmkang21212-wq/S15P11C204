package com.example.laive.growth.roadmap.service;

import com.example.laive.growth.domain.GrowthMetricType;
import com.example.laive.growth.roadmap.ai.GrowthRoadmapInput;
import com.example.laive.growth.roadmap.domain.RoadmapQuality;
import com.example.laive.growth.roadmap.repository.GrowthRoadmapRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.HexFormat;

@Service
public class GrowthRoadmapInputService {
    private final GrowthRoadmapRepository repository;
    private final PortfolioRoadmapDataProvider portfolioProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GrowthRoadmapInputService(
            GrowthRoadmapRepository repository,
            PortfolioRoadmapDataProvider portfolioProvider,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.portfolioProvider = portfolioProvider;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GrowthRoadmapInput build(long streamerId, String promptVersion) {
        long reportCount = repository.countEligibleReports(streamerId);
        if (reportCount == 0) {
            throw new IllegalStateException("No eligible report for growth roadmap.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate to = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate from = to.minusWeeks(11)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant statisticsFrom = clock.instant().minusSeconds(30L * 24 * 60 * 60);
        var statistics = repository.findRecentStatistics(streamerId, statisticsFrom);
        EnumMap<GrowthMetricType, BigDecimal> values =
                new EnumMap<>(GrowthMetricType.class);
        values.put(GrowthMetricType.AVG_VIEWERS, zero(statistics.averageViewers()));
        values.put(GrowthMetricType.TOTAL_CHAT, BigDecimal.valueOf(statistics.totalChat()));
        values.put(GrowthMetricType.BROADCAST_MINUTES,
                BigDecimal.valueOf(statistics.totalBroadcastMinutes()));
        values.put(GrowthMetricType.BROADCASTS_PER_WEEK,
                zero(statistics.broadcastsPerWeek()));

        GrowthRoadmapInput input = new GrowthRoadmapInput(
                streamerId,
                portfolioProvider.find(streamerId),
                repository.findWeeklyTrends(streamerId, from, to),
                repository.findRecentReports(streamerId, 5),
                statistics,
                repository.findPreviousSuggestions(streamerId, 20),
                repository.findAcceptedGoals(streamerId),
                values,
                RoadmapQuality.fromReportCount(reportCount),
                promptVersion,
                ""
        );
        return new GrowthRoadmapInput(
                input.streamerId(), input.portfolio(), input.weeklyTrends(),
                input.recentReports(), input.statistics(), input.previousSuggestions(),
                input.acceptedGoals(), input.currentValues(), input.quality(),
                input.promptVersion(), fingerprint(input)
        );
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fingerprint(GrowthRoadmapInput input) {
        try {
            byte[] json = objectMapper.writeValueAsString(input)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json)
            );
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Cannot fingerprint growth roadmap input.", exception
            );
        }
    }
}
